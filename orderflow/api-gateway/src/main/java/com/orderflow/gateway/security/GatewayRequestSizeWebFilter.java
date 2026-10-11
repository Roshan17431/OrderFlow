package com.orderflow.gateway.security;

import com.orderflow.gateway.config.GatewayHttpProperties;
import com.orderflow.platform.error.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayRequestSizeWebFilter implements WebFilter, Ordered {

    private final GatewayHttpProperties properties;
    private final GatewaySecurityErrorWriter errors;

    public GatewayRequestSizeWebFilter(
            GatewayHttpProperties properties,
            GatewaySecurityErrorWriter errors) {
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > properties.maxRequestSize().toBytes()) {
            return errors.write(
                    exchange,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "The request payload exceeds the configured limit");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -190;
    }
}
