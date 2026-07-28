package com.orderflow.inventory.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.inventory.controller.InventoryController;
import com.orderflow.inventory.correlation.CorrelationIdFilter;
import com.orderflow.inventory.dto.InventoryStockResponse;
import com.orderflow.inventory.service.InventoryManagementService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import({
    InventorySecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiSecurityErrorWriter.class,
    RevocationValidationFilter.class,
    CorrelationIdFilter.class,
    InventorySecurityMvcTest.JwtTestConfiguration.class
})
class InventorySecurityMvcTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean
    private InventoryManagementService inventory;

    @MockBean
    private RevocationStateService revocations;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void validSecurityState() {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        when(inventory.get(PRODUCT_ID))
                .thenReturn(new InventoryStockResponse(PRODUCT_ID, 0, 0, 0, 0, null));
    }

    @Test
    void missingTokenUsesApiErrorWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/inventory/products/{id}", PRODUCT_ID)
                        .header("X-Correlation-ID", "inventory-auth-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("inventory-auth-test"));
    }

    @Test
    void customerIsDeniedWhileVendorAndAdminAreAuthorized() throws Exception {
        mockMvc.perform(get("/api/inventory/products/{id}", PRODUCT_ID)
                        .with(token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/inventory/products/{id}", PRODUCT_ID)
                        .with(token("VENDOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventory/reservations/order/{id}", UUID.randomUUID())
                        .with(token("VENDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void zeroAdjustmentIsRejectedBeforeBusinessLogic() throws Exception {
        mockMvc.perform(post("/api/inventory/products/{id}/adjustments", PRODUCT_ID)
                        .with(token("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":0,"reason":"INVALID"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void revokedStaleAndUnavailableSecurityStateFailClosed() throws Exception {
        for (RevocationStateService.Validation validation :
                java.util.List.of(
                        RevocationStateService.Validation.REVOKED,
                        RevocationStateService.Validation.STALE)) {
            when(revocations.validate(any(), any(), anyLong())).thenReturn(validation);
            mockMvc.perform(get("/api/inventory/products/{id}", PRODUCT_ID)
                            .with(token("VENDOR")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        }
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.UNAVAILABLE);
        mockMvc.perform(get("/api/inventory/products/{id}", PRODUCT_ID)
                        .with(token("VENDOR"))
                        .header("X-Correlation-ID", "inventory-security-state"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SECURITY_STATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("inventory-security-state"));
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String role) {
        return jwt()
                .jwt(token -> token
                        .subject(USER_ID.toString())
                        .claim("jti", "test-jti")
                        .claim("token_version", 0)
                        .claim("roles", java.util.List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        Converter<Jwt, AbstractAuthenticationToken> inventoryJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
