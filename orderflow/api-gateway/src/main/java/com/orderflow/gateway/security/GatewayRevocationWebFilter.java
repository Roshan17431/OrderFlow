package com.orderflow.gateway.security;

import com.orderflow.platform.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayRevocationWebFilter implements WebFilter, Ordered {

    private static final String REVOKED_JTI_PREFIX = "security:revoked:jti:";
    private static final String TOKEN_VERSION_PREFIX = "security:user:token-version:";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/platform/ping",
            "/api/platform/info",
            "/api/auth/csrf",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/oauth2/exchange",
            "/api/auth/.well-known/jwks.json",
            "/api/auth/logout");
    private final ReactiveStringRedisTemplate redis;
    private final GatewaySecurityErrorWriter writer;

    public GatewayRevocationWebFilter(
            ReactiveStringRedisTemplate redis,
            GatewaySecurityErrorWriter writer) {
        this.redis = redis;
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = sanitize(exchange);
        String path = sanitized.getRequest().getURI().getPath();
        if (isPublic(path)) {
            return chain.filter(sanitized);
        }
        return sanitized.getPrincipal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optional -> optional
                        .map(token -> validate(token)
                                .flatMap(valid -> valid
                                        ? chain.filter(withTrustedHeaders(sanitized, token))
                                        : writer.write(
                                                sanitized,
                                                HttpStatus.UNAUTHORIZED,
                                                ErrorCode.TOKEN_REVOKED,
                                                "The access token has been revoked."))
                                .onErrorResume(exception -> writer.write(
                                        sanitized,
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        ErrorCode.SECURITY_STATE_UNAVAILABLE,
                                        "Security state is temporarily unavailable.")))
                        .orElseGet(() -> chain.filter(sanitized)));
    }

    private Mono<Boolean> validate(JwtAuthenticationToken token) {
        Number version = token.getToken().getClaim("token_version");
        if (token.getToken().getId() == null
                || token.getToken().getSubject() == null
                || version == null) {
            return Mono.just(false);
        }
        Mono<Boolean> revoked = redis.hasKey(REVOKED_JTI_PREFIX + token.getToken().getId());
        Mono<String> currentVersion = redis.opsForValue()
                .get(TOKEN_VERSION_PREFIX + token.getToken().getSubject())
                .switchIfEmpty(Mono.error(new IllegalStateException("token version unavailable")));
        return Mono.zip(revoked, currentVersion)
                .map(state -> !state.getT1()
                        && Long.parseLong(state.getT2()) == version.longValue());
    }

    private ServerWebExchange sanitize(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-ID");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Roles");
                    headers.remove("X-Authenticated-User-ID");
                    headers.remove("X-Authenticated-User-Email");
                    headers.remove("X-Authenticated-User-Roles");
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withTrustedHeaders(
            ServerWebExchange exchange,
            JwtAuthenticationToken token) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set("X-Authenticated-User-ID", token.getToken().getSubject());
                    headers.set(
                            "X-Authenticated-User-Email",
                            token.getToken().getClaimAsString("email"));
                    headers.set(
                            "X-Authenticated-User-Roles",
                            String.join(
                                    ",",
                                    Optional.ofNullable(token.getToken()
                                                    .getClaimAsStringList("roles"))
                                            .orElse(List.of())));
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/actuator/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/");
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHORIZATION.getOrder() + 1;
    }
}
