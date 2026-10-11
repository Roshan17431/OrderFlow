package com.orderflow.payment.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.payment.correlation.PaymentRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class DatabaseOutageErrorContractTest {
    @Test
    void databaseOutageReturnsSanitizedServiceUnavailable() {
        var request = new MockHttpServletRequest("POST", "/api/payments/orders/id/confirm");
        request.setAttribute(PaymentRequestAttributes.CORRELATION_ID, "db-outage-payment");

        var response = new GlobalExceptionHandler().databaseUnavailable(
                new DataAccessResourceFailureException("payment database credentials"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("credentials");
        assertThat(response.getBody().correlationId()).isEqualTo("db-outage-payment");
    }
}
