package com.orderflow.gateway.error;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.gateway.correlation.GatewayCorrelationContext;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.http.CorrelationHeaders;
import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        ErrorDescriptor descriptor = describe(throwable);
        if (descriptor.status().is5xxServerError()
                && descriptor.code() == ErrorCode.INTERNAL_ERROR) {
            LOGGER.error("Unexpected gateway request failure", throwable);
        }

        String correlationId =
                exchange.getAttribute(GatewayCorrelationContext.EXCHANGE_ATTRIBUTE);
        ApiError error = new ApiError(
                Instant.now(),
                descriptor.status().value(),
                descriptor.status().getReasonPhrase(),
                descriptor.code().name(),
                descriptor.message(),
                exchange.getRequest().getURI().getPath(),
                correlationId,
                List.of());

        exchange.getResponse().setStatusCode(descriptor.status());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            exchange.getResponse().getHeaders().set(
                    CorrelationHeaders.CORRELATION_ID,
                    correlationId);
        }
        try {
            byte[] body = objectMapper.writeValueAsBytes(error);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JacksonException serializationFailure) {
            LOGGER.error("Failed to serialize gateway error response", serializationFailure);
            return exchange.getResponse().setComplete();
        }
    }

    private ErrorDescriptor describe(Throwable throwable) {
        if (throwable instanceof NotFoundException
                || throwable instanceof WebClientRequestException
                || hasCause(throwable, ConnectException.class)) {
            return new ErrorDescriptor(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "The requested service is temporarily unavailable");
        }
        if (throwable instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return new ErrorDescriptor(
                        status,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "The requested resource was not found");
            }
            if (status != null && status.is4xxClientError()) {
                return new ErrorDescriptor(
                        status,
                        ErrorCode.BAD_REQUEST,
                        "The request could not be processed");
            }
        }
        return new ErrorDescriptor(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred");
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record ErrorDescriptor(HttpStatus status, ErrorCode code, String message) {
    }
}
