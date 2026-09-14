package io.tenoro.app.domain.exception;

import java.util.Map;

/**
 * Raised when a request conflicts with current persisted state
 * (duplicate location, insufficient origin stock, destination overflow).
 * Maps to HTTP {@code 409}.
 */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
