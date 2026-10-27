package com.orderflow.cart.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductResolutionContractTest {

    private final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build();

    @Test
    void acceptsTheProductResolutionV1ContractWithoutLosingPrecision() throws Exception {
        String fixture = """
                {"items":[{"productId":"30000000-0000-0000-0000-000000000003",
                "name":"Contract product","priceAmount":123456789.123456,"currency":"USD",
                "purchasable":true,"productVersion":7}]}
                """;

        ProductResolutionResponse response = mapper.readValue(fixture, ProductResolutionResponse.class);
        ProductResolutionItem item = response.items().getFirst();
        JsonNode json = mapper.readTree(fixture).path("items").get(0);

        assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "productId", "name", "priceAmount", "currency", "purchasable", "productVersion"));
        assertThat(item.priceAmount()).isEqualByComparingTo(new BigDecimal("123456789.123456"));
        assertThat(item.currency()).isEqualTo("USD");
        assertThat(item.productVersion()).isEqualTo(7L);
    }

    @Test
    void rejectsAnIncompatiblePurchasableProductResponse() {
        String missingRequiredPrice = """
                {"items":[{"productId":"30000000-0000-0000-0000-000000000003",
                "name":"Broken contract","currency":"USD","purchasable":true,"productVersion":7}]}
                """;

        assertThatThrownBy(() -> mapper.readValue(missingRequiredPrice, ProductResolutionResponse.class))
                .hasRootCauseInstanceOf(NullPointerException.class)
                .hasRootCauseMessage("priceAmount is required for a purchasable product");
    }
}
