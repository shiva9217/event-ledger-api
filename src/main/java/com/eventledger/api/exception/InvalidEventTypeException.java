package com.eventledger.api.exception;

public class InvalidEventTypeException extends RuntimeException {

    public InvalidEventTypeException(String type) {
        super("Invalid event type: '" + type + "'. Must be CREDIT or DEBIT");
    }
}
