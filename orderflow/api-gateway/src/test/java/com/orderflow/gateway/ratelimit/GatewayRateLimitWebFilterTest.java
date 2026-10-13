package com.orderflow.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.gateway.config.GatewayRateLimitProperties;
import com.orderflow.gateway.config.GatewayRateLimitProperties.Policy;
import com.orderflow.gateway.correlation.GatewayCorrelationContext;
import com.orderflow.gateway.security.GatewaySecurityErrorWriter;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayRateLimitWebFilterTest {

    private RateLimitStore store;
    private GatewayRateLimitWebFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        store = mock(RateLimitStore.class);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        Policy twoPerMinute = new Policy(2, Duration.ofMinutes(1));
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties(
                true,
                "test-rate-limit",
                twoPerMinute,
                twoPerMinute,
                twoPerMinute,
                twoPerMinute,
                twoPerMinute,
                twoPerMinute);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        filter = new GatewayRateLimitWebFilter(
                properties, store, new GatewaySecurityErrorWriter(mapper));
    }

    @Test
    void requestBelowThresholdContinuesAndExceededRequestReturnsApiError() {
        when(store.consume(any(), eq(2), eq(Duration.ofMinutes(1))))
                .thenReturn(Mono.just(new RateLimitStore.Decision(
                        true, 1, Duration.ofSeconds(45))))
                .thenReturn(Mono.just(new RateLimitStore.Decision(
                        false, 0, Duration.ofSeconds(45))));

        MockServerWebExchange allowed = loginExchange("rate-limit-allowed");
        StepVerifier.create(filter.filter(allowed, chain)).verifyComplete();
        assertThat(allowed.getResponse().getStatusCode()).isNull();
        assertThat(allowed.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("1");

        MockServerWebExchange denied = loginExchange("rate-limit-denied");
        StepVerifier.create(filter.filter(denied, chain)).verifyComplete();
        assertThat(denied.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(denied.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("45");
        assertThat(denied.getResponse().getBodyAsString().block())
                .contains("RATE_LIMIT_EXCEEDED")
                .doesNotContain("test-rate-limit");
    }

    @Test
    void protectedRequestsUseIndependentHashedUserKeys() {
        when(store.consume(any(), eq(2), eq(Duration.ofMinutes(1))))
                .thenReturn(Mono.just(new RateLimitStore.Decision(
                        true, 1, Duration.ofSeconds(60))));

        StepVerifier.create(filter.filter(orderExchange("user-a"), chain)).verifyComplete();
        StepVerifier.create(filter.filter(orderExchange("user-b"), chain)).verifyComplete();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(store, org.mockito.Mockito.times(2))
                .consume(keys.capture(), eq(2), eq(Duration.ofMinutes(1)));
        assertThat(keys.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        assertThat(keys.getAllValues()).allSatisfy(key -> {
            assertThat(key).startsWith("test-rate-limit:order:");
            assertThat(key).doesNotContain("user-a").doesNotContain("user-b");
        });
    }

    @Test
    void actualOrderPaymentConfirmationRouteConsumesItsRateLimit() {
        when(store.consume(any(), eq(2), eq(Duration.ofMinutes(1))))
                .thenReturn(Mono.just(new RateLimitStore.Decision(true, 1, Duration.ofSeconds(60))));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/payments/orders/00000000-0000-4000-8000-000000000001/confirm")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 12345)).build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(store).consume(any(), eq(2), eq(Duration.ofMinutes(1)));
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("1");
    }

    @Test
    void redisFailureFailsClosedForHighRiskRoutes() {
        when(store.consume(any(), eq(2), eq(Duration.ofMinutes(1))))
                .thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
        MockServerWebExchange exchange = loginExchange("redis-failure");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("SECURITY_STATE_UNAVAILABLE")
                .doesNotContain("redis unavailable");
    }

    private MockServerWebExchange loginExchange(String correlationId) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .remoteAddress(new InetSocketAddress("192.0.2.10", 12345))
                        .build());
        exchange.getAttributes().put(
                GatewayCorrelationContext.EXCHANGE_ATTRIBUTE, correlationId);
        return exchange;
    }

    private ServerWebExchange orderExchange(String user) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/orders").build());
        exchange.getAttributes().put(
                GatewayCorrelationContext.EXCHANGE_ATTRIBUTE, "order-rate-limit");
        return exchange.mutate().principal(Mono.just(() -> user)).build();
    }
}
