package com.eventledger.api.service;

import com.eventledger.api.domain.Event;
import com.eventledger.api.domain.EventType;
import com.eventledger.api.dto.BalanceResponse;
import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.exception.EventNotFoundException;
import com.eventledger.api.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    @Transactional
    public EventResponse createEvent(EventRequest request) {
        Optional<Event> existing = eventRepository.findById(request.getEventId());
        if (existing.isPresent()) {
            return eventMapper.toResponse(existing.get());
        }

        Event saved = eventRepository.save(eventMapper.toEntity(request));
        return eventMapper.toResponse(saved);
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

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        BigDecimal credits = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.CREDIT);
        BigDecimal debits = eventRepository.sumAmountByAccountIdAndType(accountId, EventType.DEBIT);
        long count = eventRepository.countByAccountId(accountId);

        BigDecimal balance = credits.subtract(debits);

        String currency = eventRepository
                .findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .findFirst()
                .map(Event::getCurrency)
                .orElse("USD");

        return BalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .currency(currency)
                .eventCount(count)
                .build();
    }
}
