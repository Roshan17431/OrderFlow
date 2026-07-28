package com.orderflow.inventory.client;

import com.orderflow.inventory.error.InventoryDomainException;
import com.orderflow.platform.error.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProductClient(
            RestClient inventoryProductRestClient,
            CircuitBreakerRegistry inventoryProductCircuitBreakers,
            RetryRegistry inventoryProductRetries) {
        this.restClient = inventoryProductRestClient;
        this.circuitBreaker =
                inventoryProductCircuitBreakers.circuitBreaker("product-service");
        this.retry = inventoryProductRetries.retry("product-service");
    }

    public ProductManagementAccess requireManagementAccess(UUID productId) {
        Supplier<ProductManagementAccess> call =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> execute(productId));
        try {
            ProductManagementAccess response =
                    Retry.decorateSupplier(retry, call).get();
            if (response == null) {
                throw new TemporaryProductServiceException("Product service returned no response");
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new InventoryDomainException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.INVENTORY_NOT_FOUND,
                    "Product inventory was not found.");
        } catch (HttpClientErrorException exception) {
            throw new InventoryDomainException(
                    HttpStatus.valueOf(exception.getStatusCode().value()),
                    ErrorCode.INSUFFICIENT_PERMISSIONS,
                    "Product management access was denied.");
        } catch (RuntimeException exception) {
            if (temporary(exception)) {
                throw new InventoryDomainException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.PRODUCT_MANAGEMENT_ACCESS_UNAVAILABLE,
                        "Product management access is temporarily unavailable.",
                        exception);
            }
            throw exception;
        }
    }

    private ProductManagementAccess execute(UUID productId) {
        try {
            return restClient
                    .get()
                    .uri("/api/vendor/products/{productId}/management-access", productId)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 502
                                    || status.value() == 503
                                    || status.value() == 504,
                            (request, response) -> {
                                throw new TemporaryProductServiceException(
                                        "Product service is temporarily unavailable");
                            })
                    .body(ProductManagementAccess.class);
        } catch (ResourceAccessException exception) {
            throw new TemporaryProductServiceException(
                    "Product service connection failed", exception);
        }
    }

    private boolean temporary(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TemporaryProductServiceException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
