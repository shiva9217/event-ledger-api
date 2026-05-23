package com.eventledger.api.exception;

public class InvalidPageParamsException extends RuntimeException {
    public InvalidPageParamsException(String message) {
        super(message);
    }
}
