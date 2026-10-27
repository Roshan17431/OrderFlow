package com.orderflow.gateway.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.gateway.correlation.GatewayCorrelationContext;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.http.CorrelationHeaders;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewaySecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public GatewaySecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            ErrorCode code,
            String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        String correlationId =
                exchange.getAttribute(GatewayCorrelationContext.EXCHANGE_ATTRIBUTE);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            exchange.getResponse().getHeaders().set(
                    CorrelationHeaders.CORRELATION_ID,
                    correlationId);
        }
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code.name(),
                message,
                exchange.getRequest().getURI().getPath(),
                correlationId,
                List.of());
        try {
            byte[] body = objectMapper.writeValueAsBytes(error);
            return exchange.getResponse().writeWith(Mono.just(
                    exchange.getResponse().bufferFactory().wrap(body)));
        } catch (JacksonException exception) {
            return exchange.getResponse().setComplete();
        }
    }
}
