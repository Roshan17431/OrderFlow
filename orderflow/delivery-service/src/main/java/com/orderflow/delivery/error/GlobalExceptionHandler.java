package com.orderflow.delivery.error;

import com.orderflow.delivery.correlation.DeliveryRequestAttributes;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.error.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DeliveryDomainException.class)
    ResponseEntity<ApiError> domain(
            DeliveryDomainException exception, HttpServletRequest request) {
        return create(
                exception.status(), exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    ResponseEntity<ApiError> validation(Exception exception, HttpServletRequest request) {
        var result = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult()
                : ((BindException) exception).getBindingResult();
        return create(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                result.getFieldErrors().stream()
                        .map(error -> new FieldViolation(
                                error.getField(), error.getDefaultMessage(), null))
                        .toList());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(Exception exception, HttpServletRequest request) {
        return create(
                HttpStatus.CONFLICT,
                ErrorCode.DELIVERY_ASSIGNMENT_CONFLICT,
                "A concurrent delivery operation conflicted.",
                request,
                List.of());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ResponseEntity<ApiError> databaseUnavailable(
            DataAccessResourceFailureException exception, HttpServletRequest request) {
        LOGGER.error("Delivery database is unavailable", exception);
        return create(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected delivery request failure", exception);
        return create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request,
                List.of());
    }

    private ResponseEntity<ApiError> create(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                message,
                request.getRequestURI(),
                (String) request.getAttribute(DeliveryRequestAttributes.CORRELATION_ID),
                violations));
    }
}
