package com.orderflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void normalizesNullFieldErrorsToAnImmutableEmptyList() {
        ApiError error = new ApiError(
                Instant.EPOCH, 500, "Internal Server Error", "INTERNAL_ERROR",
                "Unexpected error", "/", "test-id", null);

        assertThat(error.fieldErrors()).isEmpty();
        assertThat(error.fieldErrors()).isUnmodifiable();
    }

    @Test
    void serializesTheStableCrossServiceErrorContractForRepresentativeStatuses() {
        for (int status : List.of(400, 401, 403, 404, 409, 429, 500, 503)) {
            ApiError error = new ApiError(
                    Instant.parse("2026-01-02T03:04:05Z"), status, "Contract Error",
                    "CONTRACT_ERROR", "A safe public message", "/api/example",
                    "contract-correlation", List.of(new FieldViolation("field", "is invalid", null)));

            JsonNode json = mapper.valueToTree(error);
            assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                    .containsExactlyInAnyOrderElementsOf(Set.of(
                            "timestamp", "status", "error", "code", "message", "path",
                            "correlationId", "fieldErrors"));
            assertThat(json.path("status").asInt()).isEqualTo(status);
            assertThat(json.path("fieldErrors").isArray()).isTrue();
            assertThat(json.toString())
                    .doesNotContain("stackTrace", "exception", "SQLException", "JwtException", "<html");
        }
    }
}
