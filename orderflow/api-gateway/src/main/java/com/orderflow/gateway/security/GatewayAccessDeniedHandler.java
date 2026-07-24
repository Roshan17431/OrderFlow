package com.orderflow.gateway.security;

import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final GatewaySecurityErrorWriter writer;

    public GatewayAccessDeniedHandler(GatewaySecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException denied) {
        boolean csrf = denied.getClass().getSimpleName().contains("Csrf");
        return writer.write(
                exchange,
                HttpStatus.FORBIDDEN,
                csrf ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.INSUFFICIENT_PERMISSIONS,
                csrf
                        ? "The CSRF token is missing or invalid."
                        : "You do not have permission to perform this action.");
    }
}
