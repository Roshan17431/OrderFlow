package com.orderflow.order.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.order.correlation.OrderRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class DatabaseOutageErrorContractTest {
    @Test
    void databaseOutageReturnsSanitizedServiceUnavailable() {
        var request = new MockHttpServletRequest("POST", "/api/orders");
        request.setAttribute(OrderRequestAttributes.CORRELATION_ID, "db-outage-order");

        var response = new GlobalExceptionHandler().databaseUnavailable(
                new DataAccessResourceFailureException("SQL connection refused"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("SQL", "connection refused");
        assertThat(response.getBody().correlationId()).isEqualTo("db-outage-order");
    }
}
