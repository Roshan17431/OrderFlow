package com.orderflow.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.payment.config.PaymentProperties;
import com.orderflow.payment.domain.ProviderFailureCategory;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

class ResilientPaymentProviderTest {

    @Test
    void retriesTemporaryFailuresWithinTheConfiguredBound() {
        SimulatorPaymentProvider provider = mock(SimulatorPaymentProvider.class);
        CircuitBreakerFactory<?, ?> factory = directCircuitBreakerFactory();
        PaymentProperties properties = properties(2);
        ResilientPaymentProvider resilient =
                new ResilientPaymentProvider(provider, factory, properties);
        ProviderPaymentRequest request = request();
        ProviderPaymentResult expected = new ProviderPaymentResult(
                ProviderPaymentResult.Result.PROCESSING,
                "provider-payment", "provider-attempt", null, null, false);
        when(provider.confirm(request, "tok_success"))
                .thenThrow(new ProviderTimeoutException())
                .thenReturn(expected);

        assertThat(resilient.confirm(request, "tok_success")).isEqualTo(expected);
        verify(provider, times(2)).confirm(request, "tok_success");
    }

    @Test
    void doesNotRetryDeclinesOrInvalidTokens() {
        SimulatorPaymentProvider provider = mock(SimulatorPaymentProvider.class);
        ResilientPaymentProvider resilient =
                new ResilientPaymentProvider(
                        provider, directCircuitBreakerFactory(), properties(3));
        ProviderPaymentRequest request = request();
        ProviderPaymentResult decline = new ProviderPaymentResult(
                ProviderPaymentResult.Result.DECLINED,
                "provider-payment", "provider-attempt", "DECLINED",
                ProviderFailureCategory.DECLINED, false);
        when(provider.confirm(request, "tok_declined")).thenReturn(decline);

        assertThat(resilient.confirm(request, "tok_declined")).isEqualTo(decline);
        verify(provider, times(1)).confirm(request, "tok_declined");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CircuitBreakerFactory<?, ?> directCircuitBreakerFactory() {
        CircuitBreakerFactory factory = mock(CircuitBreakerFactory.class);
        CircuitBreaker direct = new CircuitBreaker() {
            @Override
            public <T> T run(
                    Supplier<T> operation, Function<Throwable, T> fallback) {
                try {
                    return operation.get();
                } catch (Throwable failure) {
                    return fallback.apply(failure);
                }
            }
        };
        when(factory.create(anyString())).thenReturn(direct);
        return factory;
    }

    private PaymentProperties properties(int attempts) {
        return new PaymentProperties(
                "simulator", true, false, "unit-test-secret",
                Duration.ofMinutes(5), Duration.ofSeconds(2),
                Duration.ofSeconds(5), attempts, Duration.ofMillis(1), 100);
    }

    private ProviderPaymentRequest request() {
        return new ProviderPaymentRequest(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"),
                "USD", "resilience-test");
    }
}
