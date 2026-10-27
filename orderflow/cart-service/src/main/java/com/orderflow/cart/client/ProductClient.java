package com.orderflow.cart.client;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.cart.dto.ProductResolutionRequest;
import com.orderflow.cart.dto.ProductResolutionResponse;
import com.orderflow.cart.error.CartDomainException;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final ObjectMapper objectMapper;

    public ProductClient(
            RestClient productRestClient,
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            ObjectMapper objectMapper) {
        this.restClient = productRestClient;
        this.circuitBreaker = circuitBreakers.circuitBreaker("product-service");
        this.retry = retries.retry("product-service");
        this.objectMapper = objectMapper;
    }

    public ProductResolutionResponse resolve(List<UUID> productIds) {
        Supplier<ProductResolutionResponse> protectedCall =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> execute(productIds));
        Supplier<ProductResolutionResponse> decorated =
                Retry.decorateSupplier(retry, protectedCall);
        try {
            ProductResolutionResponse response = decorated.get();
            if (response == null) {
                throw new TemporaryProductServiceException(
                        "Product service returned no response");
            }
            return response;
        } catch (ProductClientRejectedException exception) {
            throw new CartDomainException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.PRODUCT_UNAVAILABLE,
                    "Product information could not be resolved.");
        } catch (RuntimeException exception) {
            if (ProductFailureClassifier.isTemporary(exception)) {
                throw new CartDomainException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.PRODUCT_SERVICE_UNAVAILABLE,
                        "Product service is temporarily unavailable.",
                        exception);
            }
            throw exception;
        }
    }

    private ProductResolutionResponse execute(List<UUID> productIds) {
        try {
            return restClient
                    .post()
                    .uri("/api/products/resolve")
                    .body(new ProductResolutionRequest(productIds))
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 502
                                    || status.value() == 503
                                    || status.value() == 504,
                            (request, response) -> {
                                throw new TemporaryProductServiceException(
                                        "Product service is temporarily unavailable");
                            })
                    .onStatus(
                            status -> status.is4xxClientError(),
                            (request, response) -> {
                                throw rejected(response);
                            })
                    .onStatus(
                            status -> status.is5xxServerError(),
                            (request, response) -> {
                                throw rejected(response);
                            })
                    .body(ProductResolutionResponse.class);
        } catch (ResourceAccessException exception) {
            throw new TemporaryProductServiceException(
                    "Product service connection failed", exception);
        }
    }

    private ProductClientRejectedException rejected(ClientHttpResponse response) {
        try {
            var status = response.getStatusCode();
            ApiError error = objectMapper.readValue(response.getBody(), ApiError.class);
            return new ProductClientRejectedException(status, error.code());
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            return new ProductClientRejectedException(HttpStatus.BAD_GATEWAY);
        }
    }
}
