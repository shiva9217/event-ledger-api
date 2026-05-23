package com.eventledger.api.dto;

public record CreateEventResult(EventResponse event, boolean isNew) {
}
