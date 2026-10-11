package com.orderflow.payment.provider;

import com.orderflow.payment.config.PaymentProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.function.Supplier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.stereotype.Component;

@Component
public class ResilientPaymentProvider {

    private final PaymentProvider provider;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final PaymentProperties properties;

    public ResilientPaymentProvider(
            SimulatorPaymentProvider provider,
            CircuitBreakerFactory<?, ?> circuitBreakers,
            PaymentProperties properties) {
        this.provider = provider;
        this.circuitBreakers = circuitBreakers;
        this.properties = properties;
    }

    public String register(ProviderPaymentRequest request) {
        return run("payment-registration", () -> provider.register(request));
    }

    public ProviderPaymentResult confirm(ProviderPaymentRequest request, String token) {
        Supplier<ProviderPaymentResult> operation =
                () -> provider.confirm(request, token);
        Retry retry = Retry.of(
                "payment-confirmation",
                RetryConfig.<ProviderPaymentResult>custom()
                        .maxAttempts(properties.providerRetryAttempts())
                        .waitDuration(properties.providerRetryWait())
                        .retryOnException(this::temporaryFailure)
                        .retryOnResult(ProviderPaymentResult::retryable)
                        .build());
        return execute(
                "payment-confirmation",
                Retry.decorateSupplier(retry, operation));
    }

    public ProviderCancellationResult cancel(String providerPaymentId, String reference) {
        return run(
                "payment-cancellation",
                () -> provider.cancel(providerPaymentId, reference));
    }

    public ProviderRefundResult refund(ProviderRefundRequest request) {
        return run("payment-refund", () -> provider.refund(request));
    }

    public ProviderPaymentStatus lookup(String providerPaymentId) {
        return run("payment-status-lookup", () -> provider.lookup(providerPaymentId));
    }

    public PaymentProvider rawProvider() {
        return provider;
    }

    private <T> T run(String name, Supplier<T> operation) {
        Retry retry = Retry.of(
                name,
                RetryConfig.custom()
                        .maxAttempts(properties.providerRetryAttempts())
                        .waitDuration(properties.providerRetryWait())
                        .retryOnException(this::temporaryFailure)
                        .build());
        return execute(name, Retry.decorateSupplier(retry, operation));
    }

    private <T> T execute(String name, Supplier<T> operation) {
        try {
            return circuitBreakers.create(name).run(operation);
        } catch (NoFallbackAvailableException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ProviderTimeoutException timeout) {
                throw timeout;
            }
            if (cause instanceof ProviderUnavailableException unavailable) {
                throw unavailable;
            }
            if (cause instanceof CallNotPermittedException) {
                throw new ProviderUnavailableException();
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
    }

    private boolean temporaryFailure(Throwable failure) {
        return failure instanceof ProviderTimeoutException
                || failure instanceof ProviderUnavailableException;
    }
}
