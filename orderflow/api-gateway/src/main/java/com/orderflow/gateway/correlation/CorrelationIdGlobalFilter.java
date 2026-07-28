package com.orderflow.gateway.correlation;

import com.orderflow.platform.http.CorrelationHeaders;
import com.orderflow.platform.http.CorrelationIdValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, WebFilter, Ordered {

    public static final int FILTER_ORDER = -200;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return applyCorrelation(exchange, chain::filter);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return applyCorrelation(exchange, chain::filter);
    }

    private Mono<Void> applyCorrelation(
            ServerWebExchange exchange,
            java.util.function.Function<ServerWebExchange, Mono<Void>> next) {
        if (exchange.getAttribute(GatewayCorrelationContext.EXCHANGE_ATTRIBUTE) != null) {
            return next.apply(exchange);
        }
        String correlationId = CorrelationIdValidator.validateOrGenerate(
                exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.CORRELATION_ID));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(CorrelationHeaders.CORRELATION_ID, correlationId);
                    headers.remove("X-User-ID");
                    headers.remove("X-Customer-ID");
                    headers.remove("X-Vendor-ID");
                    headers.remove("X-Roles");
                })
                .build();
        ServerWebExchange correlatedExchange = exchange.mutate().request(request).build();
        correlatedExchange.getAttributes().put(
                GatewayCorrelationContext.EXCHANGE_ATTRIBUTE,
                correlationId);
        correlatedExchange.getResponse().getHeaders().set(
                CorrelationHeaders.CORRELATION_ID,
                correlationId);
        correlatedExchange.getResponse().beforeCommit(() -> {
            correlatedExchange.getResponse().getHeaders().set(
                    CorrelationHeaders.CORRELATION_ID,
                    correlationId);
            return Mono.empty();
        });

        return next.apply(correlatedExchange)
                .contextWrite(Context.of(
                        GatewayCorrelationContext.REACTOR_CONTEXT_KEY,
                        correlationId));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
