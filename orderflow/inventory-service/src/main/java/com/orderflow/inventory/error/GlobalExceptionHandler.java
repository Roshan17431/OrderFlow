package com.orderflow.inventory.error;

import com.orderflow.inventory.correlation.InventoryRequestAttributes;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.error.FieldViolation;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<ApiError> domain(
            InventoryDomainException exception, HttpServletRequest request) {
        return create(
                exception.status(), exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> validation(Exception exception, HttpServletRequest request) {
        var result = exception instanceof MethodArgumentNotValidException method
                ? method.getBindingResult()
                : ((BindException) exception).getBindingResult();
        List<FieldViolation> violations = result.getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(), error.getDefaultMessage(), null))
                .toList();
        return create(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return create(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "The request is invalid.",
                request,
                List.of());
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ApiError> concurrent(Exception exception, HttpServletRequest request) {
        return create(
                HttpStatus.CONFLICT,
                ErrorCode.INVENTORY_RESERVATION_CONFLICT,
                "Inventory was modified by another request.",
                request,
                List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> notFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return create(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(
            Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected inventory request failure", exception);
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
                (String) request.getAttribute(InventoryRequestAttributes.CORRELATION_ID),
                violations));
    }
}
