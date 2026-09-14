package io.tenoro.app.domain.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base type for expected inventory domain failures.
 *
 * <p>Each failure carries a stable machine-readable {@code code} and an
 * object-valued {@code details} map so the infrastructure layer can render a
 * structured API error without inspecting exception messages. Domain code stays
 * free of any framework or HTTP dependency; the mapping to HTTP status codes
 * lives in the infrastructure exception advice.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    protected DomainException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
