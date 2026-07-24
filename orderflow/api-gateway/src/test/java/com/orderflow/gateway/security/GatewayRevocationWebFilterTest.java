package com.orderflow.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayRevocationWebFilterTest {

    private ReactiveStringRedisTemplate redis;
    private ReactiveValueOperations<String, String> values;
    private WebFilterChain chain;
    private GatewayRevocationWebFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        chain = mock(WebFilterChain.class);
        when(redis.opsForValue()).thenReturn(values);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        filter = new GatewayRevocationWebFilter(
                redis,
                new GatewaySecurityErrorWriter(new ObjectMapper()));
    }

    @Test
    void validStateContinuesAndClientIdentityHeadersAreOverwritten() {
        when(redis.hasKey("security:revoked:jti:jti")).thenReturn(Mono.just(false));
        when(values.get("security:user:token-version:user-id")).thenReturn(Mono.just("2"));
        ServerWebExchange exchange = exchange("/api/security/authenticated", 2);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(org.mockito.ArgumentMatchers.argThat(forwarded ->
                "user-id".equals(forwarded.getRequest().getHeaders()
                        .getFirst("X-Authenticated-User-ID"))
                        && !"attacker".equals(forwarded.getRequest().getHeaders()
                                .getFirst("X-User-ID"))));
    }

    @Test
    void revokedStaleAndUnavailableStateFailClosed() {
        when(redis.hasKey("security:revoked:jti:jti")).thenReturn(Mono.just(true));
        when(values.get("security:user:token-version:user-id")).thenReturn(Mono.just("2"));
        ServerWebExchange revoked = exchange("/api/security/authenticated", 2);
        StepVerifier.create(filter.filter(revoked, chain)).verifyComplete();
        assertThat(revoked.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(redis.hasKey("security:revoked:jti:jti")).thenReturn(Mono.just(false));
        ServerWebExchange stale = exchange("/api/security/authenticated", 1);
        StepVerifier.create(filter.filter(stale, chain)).verifyComplete();
        assertThat(stale.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(redis.hasKey("security:revoked:jti:jti"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        ServerWebExchange unavailable = exchange("/api/security/authenticated", 2);
        StepVerifier.create(filter.filter(unavailable, chain)).verifyComplete();
        assertThat(unavailable.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void publicRequestDoesNotReadRedis() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(org.mockito.ArgumentMatchers.any());
    }

    private ServerWebExchange exchange(String path, long version) {
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "user-id",
                        "jti", "jti",
                        "email", "person@example.com",
                        "roles", List.of("CUSTOMER"),
                        "token_version", version));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
                        .header("X-User-ID", "attacker"))
                .mutate()
                .principal(Mono.just(authentication))
                .build();
    }
}
