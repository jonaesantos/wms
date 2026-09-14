package io.tenoro.app.domain.exception;

import java.util.Map;

/**
 * Raised when a request violates a domain invariant on its inputs
 * (invalid identifier, unsupported type, negative or non-positive quantity).
 * Maps to HTTP {@code 400}.
 */
public class ValidationException extends DomainException {

    public static final String CODE = "VALIDATION_ERROR";

    public ValidationException(String message, Map<String, Object> details) {
        super(CODE, message, details);
    }

    public ValidationException(String message) {
        super(CODE, message, null);
    }
}
