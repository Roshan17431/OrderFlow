package com.orderflow.product.dto;

import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

class ProductDetailResponseBoot3CompatibilityTest {
    private final ObjectMapper mapper = JsonMapper.builder().configureForJackson2()
            .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void readsExistingBoot3Json() throws Exception {
        var value = new ProductDetailResponse(java.util.UUID.fromString("30000000-0000-0000-0000-000000000003"), "precision-item", "Precision item", null, new java.math.BigDecimal("41152263.041152"), "USD", new CategorySummary(java.util.UUID.fromString("40000000-0000-0000-0000-000000000004"), "Test category", "test-category"), java.time.Instant.parse("2026-01-02T03:04:05.123456Z"), java.time.Instant.parse("2026-01-02T03:04:05.123456Z"), 7L);
        String name = "/compat/boot3/ProductDetailResponse.json";
        try (var input = getClass().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            assertThat(mapper.readValue(input, ProductDetailResponse.class)).isEqualTo(value);
        }
    }
    @Test
    void boot3RollbackCanReadNewJson() throws Exception {
        var old = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try (var input = getClass().getResourceAsStream("/compat/boot3/ProductDetailResponse.json")) {
            var value = mapper.readValue(input, ProductDetailResponse.class);
            assertThat(old.readValue(mapper.writeValueAsString(value), ProductDetailResponse.class)).isEqualTo(value);
        }
    }
}
