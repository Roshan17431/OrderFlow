package com.orderflow.auth.error;

import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.error.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(
            Exception exception,
            HttpServletRequest request) {
        List<FieldViolation> violations;
        if (exception instanceof MethodArgumentNotValidException validationException) {
            violations = validationException.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage(), null))
                    .toList();
        } else {
            BindException bindException = (BindException) exception;
            violations = bindException.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage(), null))
                    .toList();
        }
        return create(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                request,
                violations);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(
            Exception exception,
            HttpServletRequest request) {
        return create(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "The request is invalid",
                request,
                List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            Exception exception,
            HttpServletRequest request) {
        return create(
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this resource",
                request,
                List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            Exception exception,
            HttpServletRequest request) {
        return create(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "The request media type is not supported",
                request,
                List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        return create(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                exception.getMessage(),
                request,
                List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return create(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found",
                request,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unexpected request failure", exception);
        return create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request,
                List.of());
    }

    private ResponseEntity<ApiError> create(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        ApiError error = new ApiError(
                    Instant.now(),
                    status.value(),
                    status.getReasonPhrase(),
                    code.name(),
                    message,
                    request.getRequestURI(),
                    (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID),
                    violations);
        return ResponseEntity.status(status).body(error);
    }
}
