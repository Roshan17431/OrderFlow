package com.orderflow.order.client;

import com.orderflow.order.error.OrderDomainException;
import com.orderflow.platform.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CartClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CartClient(
            RestClient orderCartRestClient,
            CircuitBreakerRegistry orderCartCircuitBreakers,
            RetryRegistry orderCartRetries) {
        this.restClient = orderCartRestClient;
        this.circuitBreaker = orderCartCircuitBreakers.circuitBreaker("cart-service");
        this.retry = orderCartRetries.retry("cart-service");
    }

    public CartSnapshot get() {
        return protectedCall(() -> restClient.get()
                .uri("/api/cart")
                .retrieve()
                .onStatus(
                        status -> status.value() == 502
                                || status.value() == 503
                                || status.value() == 504,
                        (request, response) -> {
                            throw new TemporaryCartServiceException(
                                    "Cart service is temporarily unavailable");
                        })
                .body(CartSnapshot.class));
    }

    public CartValidationResult validate() {
        return protectedCall(() -> restClient.post()
                .uri("/api/cart/validate")
                .retrieve()
                .onStatus(
                        status -> status.value() == 502
                                || status.value() == 503
                                || status.value() == 504,
                        (request, response) -> {
                            throw new TemporaryCartServiceException(
                                    "Cart service is temporarily unavailable");
                        })
                .body(CartValidationResult.class));
    }

    private <T> T protectedCall(Supplier<T> operation) {
        Supplier<T> call = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            try {
                return operation.get();
            } catch (ResourceAccessException exception) {
                throw new TemporaryCartServiceException(
                        "Cart service connection failed", exception);
            }
        });
        try {
            T response = Retry.decorateSupplier(retry, call).get();
            if (response == null) {
                throw new TemporaryCartServiceException("Cart service returned no response");
            }
            return response;
        } catch (HttpClientErrorException exception) {
            throw new OrderDomainException(
                    HttpStatus.valueOf(exception.getStatusCode().value()),
                    ErrorCode.ORDER_CART_INVALID,
                    "Cart validation was rejected.");
        } catch (RuntimeException exception) {
            if (temporary(exception)) {
                throw new OrderDomainException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.CART_SERVICE_UNAVAILABLE,
                        "Cart service is temporarily unavailable.",
                        exception);
            }
            throw exception;
        }
    }

    private boolean temporary(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TemporaryCartServiceException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
