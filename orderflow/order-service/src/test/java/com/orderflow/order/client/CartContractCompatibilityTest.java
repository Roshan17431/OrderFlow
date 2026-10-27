package com.orderflow.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CartContractCompatibilityTest {

    private final ObjectMapper mapper = JsonMapper.builder().configureForJackson2().disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final String cart = """
            {"userId":"20000000-0000-0000-0000-000000000002","version":7,"currency":"USD",
            "items":[{"productId":"30000000-0000-0000-0000-000000000003",
            "productNameSnapshot":"Contract product","quantity":3,"unitPriceSnapshot":41.123456,
            "currency":"USD","productVersionSnapshot":9,"purchasable":true,"priceChanged":false,
            "addedAt":"2026-01-02T03:04:05Z","updatedAt":"2026-01-02T03:04:06Z"}],
            "distinctItemCount":1,"totalQuantity":3,"subtotal":123.370368,
            "createdAt":"2026-01-02T03:04:00Z","updatedAt":"2026-01-02T03:04:06Z",
            "expiresAt":"2026-01-02T04:04:00Z"}
            """;

    @Test
    void consumesGetCartAndValidationContracts() throws Exception {
        CartSnapshot snapshot = mapper.readValue(cart, CartSnapshot.class);
        CartValidationResult validation = mapper.readValue(
                "{\"cart\":" + cart + ",\"valid\":true,\"issues\":[]}", CartValidationResult.class);

        assertThat(snapshot.version()).isEqualTo(7);
        assertThat(snapshot.items().getFirst().quantity()).isEqualTo(3);
        assertThat(snapshot.items().getFirst().unitPriceSnapshot())
                .isEqualByComparingTo(new BigDecimal("41.123456"));
        assertThat(snapshot.subtotal()).isEqualByComparingTo("123.370368");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
    }

    @Test
    void rejectsAResponseWithAMissingRequiredCartField() {
        String incompatible = cart.replace("\"currency\":\"USD\",", "");
        assertThatThrownBy(() -> mapper.readValue(incompatible, CartSnapshot.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("currency is invalid");
    }
}
