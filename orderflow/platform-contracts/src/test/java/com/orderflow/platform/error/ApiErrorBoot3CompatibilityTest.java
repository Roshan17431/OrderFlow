package com.orderflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class ApiErrorBoot3CompatibilityTest {
    private final ObjectMapper mapper = JsonMapper.builder().configureForJackson2()
            .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void readsExistingBoot3Json() throws Exception {
        var value = new ApiError(java.time.Instant.parse("2026-01-02T03:04:05.123456Z"), 400, "Bad Request", "VALIDATION_FAILED", "Invalid request", "/api/products", "compat-correlation", java.util.List.of(new FieldViolation("price", "must be positive", null)));
        String name = "/compat/boot3/ApiError.json";
        try (var input = getClass().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            assertThat(mapper.readValue(input, ApiError.class)).isEqualTo(value);
        }
    }
    @Test
    void boot3RollbackCanReadNewJson() throws Exception {
        var old = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try (var input = getClass().getResourceAsStream("/compat/boot3/ApiError.json")) {
            var value = mapper.readValue(input, ApiError.class);
            assertThat(old.readValue(mapper.writeValueAsString(value), ApiError.class)).isEqualTo(value);
        }
    }
}
