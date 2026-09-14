package io.tenoro.app.domain.exception;

import java.util.Map;

/**
 * Raised when a request references a syntactically valid but non-existent
 * resource (e.g. an unknown location). Maps to HTTP {@code 404}.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
