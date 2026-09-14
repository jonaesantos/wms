package io.tenoro.app.domain.exception;

/**
 * Stable, machine-readable error codes returned to API clients. Kept in the
 * domain so services and tests reference the same constants the advice maps.
 */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String LOCATION_NOT_FOUND = "LOCATION_NOT_FOUND";
    public static final String LOCATION_ALREADY_EXISTS = "LOCATION_ALREADY_EXISTS";
    public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";
    public static final String STOCK_OVERFLOW = "STOCK_OVERFLOW";

    private ErrorCodes() {
    }
}
