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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    /**
     * Creates a new event or returns the existing one (idempotent).
     *
     * The DB primary key on eventId is the ultimate uniqueness guard.
     * saveAndFlush flushes within the transaction so concurrent duplicates
     * surface as DataIntegrityViolationException here, not as an unhandled 500.
     */
    @Transactional
    public CreateEventResult createEvent(EventRequest request) {
        return eventRepository.findById(request.getEventId())
                .map(existing -> new CreateEventResult(eventMapper.toResponse(existing), false))
                .orElseGet(() -> {
                    try {
                        Event saved = eventRepository.saveAndFlush(eventMapper.toEntity(request));
                        return new CreateEventResult(eventMapper.toResponse(saved), true);
                    } catch (DataIntegrityViolationException ex) {
                        // Concurrent duplicate: another request won the INSERT race.
                        // Fetch and return the persisted winner as an idempotent 200.
                        Event existing = eventRepository.findById(request.getEventId())
                                .orElseThrow(() -> ex);
                        return new CreateEventResult(eventMapper.toResponse(existing), false);
                    }
                });
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
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
        validatePageParams(page, size);

        PageRequest pageable = PageRequest.of(page, size, Sort.by("eventTimestamp").ascending());
        Page<Event> result = eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId, pageable);

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

    private void validatePageParams(int page, int size) {
        if (page < 0) {
            throw new InvalidPageParamsException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
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
        BigDecimal credits = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.CREDIT);
        BigDecimal debits  = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.DEBIT);
        long count         = eventRepository.countByAccountId(accountId);

        List<String> currencies = eventRepository.findCurrencyByAccountIdChronological(
                accountId, PageRequest.of(0, 1));
        String currency = currencies.isEmpty() ? "USD" : currencies.get(0);

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(credits.subtract(debits).setScale(2, RoundingMode.HALF_UP))
                .currency(currency)
                .eventCount(count)
                .build();
    }
}
