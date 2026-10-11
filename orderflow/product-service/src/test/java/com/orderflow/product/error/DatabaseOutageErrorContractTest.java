package com.orderflow.product.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.product.correlation.ProductRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class DatabaseOutageErrorContractTest {
    @Test
    void databaseOutageReturnsSanitizedServiceUnavailable() {
        var request = new MockHttpServletRequest("POST", "/api/vendor/products");
        request.setAttribute(ProductRequestAttributes.CORRELATION_ID, "db-outage-product");

        var response = new GlobalExceptionHandler().databaseUnavailable(
                new DataAccessResourceFailureException("password=do-not-leak"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("password", "do-not-leak");
        assertThat(response.getBody().correlationId()).isEqualTo("db-outage-product");
    }
}
