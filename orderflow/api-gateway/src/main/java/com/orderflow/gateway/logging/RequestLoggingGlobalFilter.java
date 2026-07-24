package com.orderflow.gateway.logging;

import com.orderflow.gateway.correlation.CorrelationIdGlobalFilter;
import com.orderflow.gateway.correlation.GatewayCorrelationContext;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestLoggingGlobalFilter implements GlobalFilter, WebFilter, Ordered {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);
    private static final String LOGGING_APPLIED_ATTRIBUTE =
            RequestLoggingGlobalFilter.class.getName() + ".applied";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return logCompletion(exchange, chain::filter);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return logCompletion(exchange, chain::filter);
    }

    private Mono<Void> logCompletion(
            ServerWebExchange exchange,
            java.util.function.Function<ServerWebExchange, Mono<Void>> next) {
        if (exchange.getAttribute(LOGGING_APPLIED_ATTRIBUTE) != null) {
            return next.apply(exchange);
        }
        exchange.getAttributes().put(LOGGING_APPLIED_ATTRIBUTE, Boolean.TRUE);
        long startedAt = System.nanoTime();
        return next.apply(exchange).doFinally(signalType -> {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
            int status = statusCode == null
                    ? HttpStatus.INTERNAL_SERVER_ERROR.value()
                    : statusCode.value();
            String correlationId =
                    exchange.getAttribute(GatewayCorrelationContext.EXCHANGE_ATTRIBUTE);
            LOGGER.atInfo()
                    .addKeyValue("event", "http.request.completed")
                    .addKeyValue("method", exchange.getRequest().getMethod().name())
                    .addKeyValue("path", exchange.getRequest().getURI().getPath())
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("correlationId", correlationId)
                    .log("HTTP request completed");
        });
    }

    @Override
    public int getOrder() {
        return CorrelationIdGlobalFilter.FILTER_ORDER + 10;
    }
}
