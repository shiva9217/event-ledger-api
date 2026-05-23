package com.eventledger.api.service;

import com.eventledger.api.domain.EventType;
import com.eventledger.api.domain.Event;
import com.eventledger.api.dto.BalanceResponse;
import com.eventledger.api.dto.CreateEventResult;
import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.dto.PagedResponse;
import com.eventledger.api.exception.EventNotFoundException;
import com.eventledger.api.exception.InvalidPageParamsException;
import com.eventledger.api.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * Per-eventId lock registry for application-level concurrency control.
     * Prevents redundant DB round-trips when two threads race on the same eventId.
     */
    private final ConcurrentHashMap<String, ReentrantLock> lockRegistry = new ConcurrentHashMap<>();

    /**
     * Creates a new event or returns the existing one (idempotent).
     *
     * Two-layer concurrency protection:
     *   Layer 1 — ReentrantLock per eventId serialises concurrent threads for the
     *             same eventId at the application level.
     *   Layer 2 — DB primary key + DataIntegrityViolationException catch is the
     *             ultimate fallback for any race that slips past Layer 1.
     *
     * TransactionTemplate is used (not @Transactional) so that the DB commit
     * happens INSIDE the lock boundary. With @Transactional the proxy commits
     * AFTER the method returns — after the lock is already released — which would
     * let a second thread read uncommitted data and fail with a 500.
     */
    public CreateEventResult createEvent(EventRequest request) {
        log.debug("createEvent called: eventId={}, accountId={}, type={}, amount={}",
                request.getEventId(), request.getAccountId(), request.getType(), request.getAmount());

        ReentrantLock lock = acquireLock(request.getEventId());
        try {
            return transactionTemplate.execute(status ->
                eventRepository.findById(request.getEventId())
                    .map(existing -> {
                        log.info("Duplicate event received — returning original: eventId={}", existing.getEventId());
                        return new CreateEventResult(eventMapper.toResponse(existing), false);
                    })
                    .orElseGet(() -> {
                        try {
                            Event saved = eventRepository.saveAndFlush(eventMapper.toEntity(request));
                            log.info("Event created: eventId={}, accountId={}, type={}, amount={}",
                                    saved.getEventId(), saved.getAccountId(), saved.getType(), saved.getAmount());
                            return new CreateEventResult(eventMapper.toResponse(saved), true);
                        } catch (DataIntegrityViolationException ex) {
                            // DB-level fallback: another thread won the INSERT race.
                            log.warn("DataIntegrityViolationException — concurrent duplicate detected for eventId={}; fetching original",
                                    request.getEventId());
                            Event existing = eventRepository.findById(request.getEventId())
                                    .orElseThrow(() -> ex);
                            return new CreateEventResult(eventMapper.toResponse(existing), false);
                        }
                    })
            );
        } finally {
            releaseLock(request.getEventId(), lock);
        }
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        log.debug("getEvent: eventId={}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new EventNotFoundException(eventId);
                });
        return eventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(eventMapper::toResponse)
                .toList();
    }

    /**
     * Paginated variant of getEventsByAccount.
     * Always sorted by eventTimestamp ASC — sort is not configurable.
     * Validates: page >= 0, 1 <= size <= 100.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> getEventsByAccountPaged(String accountId, int page, int size) {
        log.debug("getEventsByAccountPaged: accountId={}, page={}, size={}", accountId, page, size);
        validatePageParams(page, size);

        PageRequest pageable = PageRequest.of(page, size, Sort.by("eventTimestamp").ascending());
        Page<Event> result = eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId, pageable);
        log.debug("getEventsByAccountPaged result: accountId={}, totalElements={}, totalPages={}",
                accountId, result.getTotalElements(), result.getTotalPages());

        List<EventResponse> content = result.getContent()
                .stream()
                .map(eventMapper::toResponse)
                .toList();

        return PagedResponse.<EventResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .build();
    }

    // ── concurrency helpers ────────────────────────────────────────────────────

    private ReentrantLock acquireLock(String eventId) {
        ReentrantLock lock = lockRegistry.computeIfAbsent(eventId, k -> new ReentrantLock());
        lock.lock();
        log.debug("Lock acquired: eventId={}", eventId);
        return lock;
    }

    private void releaseLock(String eventId, ReentrantLock lock) {
        lock.unlock();
        lockRegistry.remove(eventId);
        log.debug("Lock released: eventId={}", eventId);
    }

    // ── pagination helpers ─────────────────────────────────────────────────────

    private void validatePageParams(int page, int size) {
        if (page < 0) {
            log.warn("Invalid page param: page={}", page);
            throw new InvalidPageParamsException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            log.warn("Invalid size param: size={}", size);
            throw new InvalidPageParamsException("size must be between 1 and 100");
        }
    }

    /**
     * Computes net balance: SUM(CREDIT) - SUM(DEBIT).
     * Uses 3 targeted queries instead of loading all event rows.
     * Returns 0.00 / eventCount=0 / currency="USD" when no events exist.
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        log.debug("getBalance: accountId={}", accountId);
        BigDecimal credits = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.CREDIT);
        BigDecimal debits  = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.DEBIT);
        long count         = eventRepository.countByAccountId(accountId);

        List<String> currencies = eventRepository.findCurrencyByAccountIdChronological(
                accountId, PageRequest.of(0, 1));
        String currency = currencies.isEmpty() ? "USD" : currencies.get(0);

        BigDecimal balance = credits.subtract(debits).setScale(2, RoundingMode.HALF_UP);
        log.info("Balance computed: accountId={}, balance={}, currency={}, eventCount={}",
                accountId, balance, currency, count);

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .currency(currency)
                .eventCount(count)
                .build();
    }
}
