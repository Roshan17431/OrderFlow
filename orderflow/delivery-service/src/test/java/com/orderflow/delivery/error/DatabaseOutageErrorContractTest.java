package com.orderflow.delivery.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.delivery.correlation.DeliveryRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class DatabaseOutageErrorContractTest {
    @Test
    void databaseOutageReturnsSanitizedServiceUnavailable() {
        var request = new MockHttpServletRequest("POST", "/api/delivery/agents/me/deliveries/id/fail");
        request.setAttribute(DeliveryRequestAttributes.CORRELATION_ID, "db-outage-delivery");

        var response = new GlobalExceptionHandler().databaseUnavailable(
                new DataAccessResourceFailureException("database host and secret"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("host", "secret");
        assertThat(response.getBody().correlationId()).isEqualTo("db-outage-delivery");
    }
}
