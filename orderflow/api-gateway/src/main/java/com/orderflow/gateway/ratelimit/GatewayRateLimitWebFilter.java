package com.orderflow.gateway.ratelimit;

import com.orderflow.gateway.config.GatewayRateLimitProperties;
import com.orderflow.gateway.config.GatewayRateLimitProperties.Policy;
import com.orderflow.gateway.security.GatewaySecurityErrorWriter;
import com.orderflow.platform.error.ErrorCode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class GatewayRateLimitWebFilter implements WebFilter, Ordered {

    private final GatewayRateLimitProperties properties;
    private final RateLimitStore store;
    private final GatewaySecurityErrorWriter errors;

    public GatewayRateLimitWebFilter(
            GatewayRateLimitProperties properties,
            RateLimitStore store,
            GatewaySecurityErrorWriter errors) {
        this.properties = properties;
        this.store = store;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Optional<MatchedPolicy> matched = match(exchange);
        if (!properties.enabled() || matched.isEmpty()) {
            return chain.filter(exchange);
        }
        MatchedPolicy rateLimit = matched.get();
        Mono<String> subject = rateLimit.authenticated()
                ? exchange.getPrincipal().map(Principal::getName).switchIfEmpty(Mono.just(clientKey(exchange)))
                : Mono.just(clientKey(exchange));
        return subject.flatMap(value -> store.consume(
                        redisKey(rateLimit.name(), value),
                        rateLimit.policy().requests(),
                        rateLimit.policy().window()))
                .flatMap(decision -> {
                    exchange.getResponse().getHeaders().set(
                            "X-RateLimit-Remaining", Long.toString(decision.remaining()));
                    if (decision.allowed()) {
                        return chain.filter(exchange);
                    }
                    exchange.getResponse().getHeaders().set(
                            "Retry-After", Long.toString(retryAfterSeconds(decision.retryAfter())));
                    return errors.write(
                            exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            ErrorCode.RATE_LIMIT_EXCEEDED,
                            "The request rate limit was exceeded");
                })
                .onErrorResume(ignored -> errors.write(
                        exchange,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.SECURITY_STATE_UNAVAILABLE,
                        "Rate-limit security state is temporarily unavailable"));
    }

    private Optional<MatchedPolicy> match(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return Optional.empty();
        }
        String path = exchange.getRequest().getURI().getPath();
        if (path.equals("/api/auth/login")) {
            return matched("login", properties.login(), false);
        }
        if (path.equals("/api/auth/register")) {
            return matched("registration", properties.registration(), false);
        }
        if (path.equals("/api/auth/refresh")) {
            return matched("refresh", properties.refresh(), false);
        }
        if (path.equals("/api/orders")) {
            return matched("order", properties.orderCreation(), true);
        }
        if (path.matches("/api/payments/[^/]+/confirm")) {
            return matched("payment-confirmation", properties.paymentConfirmation(), true);
        }
        if (path.startsWith("/api/payments/webhooks/")) {
            return matched("webhook", properties.webhook(), false);
        }
        return Optional.empty();
    }

    private Optional<MatchedPolicy> matched(String name, Policy policy, boolean authenticated) {
        return Optional.of(new MatchedPolicy(name, policy, authenticated));
    }

    private String redisKey(String policy, String subject) {
        return properties.namespace() + ':' + policy + ':' + hash(subject.toLowerCase(Locale.ROOT));
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long retryAfterSeconds(Duration duration) {
        return Math.max(1, (duration.toMillis() + 999) / 1000);
    }

    @Override
    public int getOrder() {
        return SecurityWebFiltersOrder.AUTHORIZATION.getOrder() + 2;
    }

    private record MatchedPolicy(String name, Policy policy, boolean authenticated) {
    }
}
