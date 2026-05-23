package com.eventledger.api.service;

import com.eventledger.api.domain.Event;
import com.eventledger.api.domain.EventType;
import com.eventledger.api.dto.EventRequest;
import com.eventledger.api.dto.EventResponse;
import com.eventledger.api.exception.InvalidEventTypeException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EventMapper {

    public Event toEntity(EventRequest request) {
        EventType eventType = parseType(request.getType());
        return Event.builder()
                .eventId(request.getEventId())
                .accountId(request.getAccountId())
                .type(eventType)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .receivedAt(Instant.now())
                .metadata(toJsonString(request.getMetadata()))
                .build();
    }

    public EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .eventId(event.getEventId())
                .accountId(event.getAccountId())
                .type(event.getType().name())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .receivedAt(event.getReceivedAt())
                .metadata(event.getMetadata())
                .build();
    }

    private EventType parseType(String type) {
        try {
            return EventType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidEventTypeException(type);
        }
    }

    /** Serialises a JsonNode to its compact JSON text for DB storage, or null if absent. */
    private String toJsonString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.toString();
    }
}
