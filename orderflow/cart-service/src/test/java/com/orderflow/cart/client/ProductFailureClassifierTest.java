package com.orderflow.cart.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ProductFailureClassifierTest {

    @Test
    void retriesOnlyTemporaryFailuresIncludingWrappedNetworkFailures() {
        assertThat(ProductFailureClassifier.isTemporary(
                        new TemporaryProductServiceException("temporary")))
                .isTrue();
        assertThat(ProductFailureClassifier.isTemporary(
                        new RuntimeException(new ConnectException("refused"))))
                .isTrue();
        assertThat(ProductFailureClassifier.isTemporary(
                        new ResourceAccessException(
                                "timeout", new SocketTimeoutException("timed out"))))
                .isTrue();
    }

    @Test
    void doesNotRetryValidationOrRejectedRequests() {
        assertThat(ProductFailureClassifier.isTemporary(
                        new ProductClientRejectedException(
                                org.springframework.http.HttpStatus.CONFLICT)))
                .isFalse();
        assertThat(ProductFailureClassifier.isTemporary(
                        new IllegalArgumentException("invalid request")))
                .isFalse();
    }
}
