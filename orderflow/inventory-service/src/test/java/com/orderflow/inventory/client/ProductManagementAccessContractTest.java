package com.orderflow.inventory.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductManagementAccessContractTest {
    private final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build();

    @Test
    void consumesTheProductManagementAccessContract() throws Exception {
        ProductManagementAccess access = mapper.readValue("""
                {"productId":"30000000-0000-0000-0000-000000000003","status":"ACTIVE",
                "vendorId":"40000000-0000-0000-0000-000000000004"}
                """, ProductManagementAccess.class);

        assertThat(access.status()).isEqualTo("ACTIVE");
        assertThat(access.productId()).isNotNull();
        assertThat(access.vendorId()).isNotNull();
    }

    @Test
    void rejectsUnknownProductStatusInsteadOfChangingOwnershipSemantics() {
        assertThatThrownBy(() -> mapper.readValue("""
                {"productId":"30000000-0000-0000-0000-000000000003","status":"FUTURE_STATUS",
                "vendorId":"40000000-0000-0000-0000-000000000004"}
                """, ProductManagementAccess.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("status is invalid");
    }
}
