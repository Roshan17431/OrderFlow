package com.orderflow.auth.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;

class DatabaseOutageErrorContractTest {
    @Test
    void databaseOutageReturnsSanitizedServiceUnavailable() {
        var request = new MockHttpServletRequest("POST", "/api/auth/register");
        request.setAttribute(CorrelationRequestAttributes.CORRELATION_ID, "db-outage-auth");

        var response = new GlobalExceptionHandler().handleDatabaseUnavailable(
                new DataAccessResourceFailureException("jdbc:postgresql://secret-host"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).doesNotContain("jdbc", "secret-host");
        assertThat(response.getBody().correlationId()).isEqualTo("db-outage-auth");
    }
}
