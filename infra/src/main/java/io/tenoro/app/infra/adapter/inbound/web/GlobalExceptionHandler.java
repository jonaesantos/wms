package io.tenoro.app.infra.adapter.inbound.web;

import io.tenoro.app.api.dto.ApiErrorResponse;
import io.tenoro.app.domain.exception.ConflictException;
import io.tenoro.app.domain.exception.DomainException;
import io.tenoro.app.domain.exception.ErrorCodes;
import io.tenoro.app.domain.exception.NotFoundException;
import io.tenoro.app.domain.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized mapping of expected failures to structured HTTP errors.
 *
 * <p>Domain failures are classified by their category ({@link ValidationException}
 * -> 400, {@link NotFoundException} -> 404, {@link ConflictException} -> 409).
 * Framework-level input failures (malformed JSON, invalid enums, missing or
 * out-of-range values, Bean Validation) are mapped to 400 rather than 500.
 * Unexpected exceptions are intentionally left to the framework so genuine
 * defects are not downgraded to client errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException ex) {
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                details.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "One or more fields are invalid", details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "Required parameter '" + ex.getParameterName() + "' is missing",
                Map.of("parameter", ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "Parameter '" + ex.getName() + "' has an invalid value",
                Map.of("parameter", ex.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return response(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "Request body is malformed or contains an invalid value",
                Map.of("reason", "unreadable-request-body"));
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, DomainException ex) {
        return response(status, ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, details));
    }
}
