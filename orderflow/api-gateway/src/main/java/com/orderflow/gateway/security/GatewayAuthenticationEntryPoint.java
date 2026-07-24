package com.orderflow.gateway.security;

import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final GatewaySecurityErrorWriter writer;

    public GatewayAuthenticationEntryPoint(GatewaySecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception) {
        boolean bearer = exchange.getRequest().getHeaders().containsKey("Authorization");
        return writer.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                bearer ? ErrorCode.INVALID_TOKEN : ErrorCode.AUTHENTICATION_REQUIRED,
                bearer ? "The access token is invalid." : "Authentication is required.");
    }
}
