package com.orderflow.cart.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.cart.controller.CartController;
import com.orderflow.cart.correlation.CorrelationIdFilter;
import com.orderflow.cart.dto.CartResponse;
import com.orderflow.cart.service.CartService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
@Import({
    CartSecurityConfiguration.class,
    CorrelationIdFilter.class,
    ApiSecurityErrorWriter.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    RevocationValidationFilter.class,
    CartSecurityMvcTest.SecurityBeans.class
})
class CartSecurityMvcTest {

    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean
    private CartService carts;

    @MockBean
    private RevocationStateService revocations;

    @MockBean
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        when(carts.get(USER_ID)).thenReturn(emptyCart());
    }

    @Test
    void missingTokenUsesApiErrorAndPreservesCorrelationId() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("X-Correlation-ID", "cart-security-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "cart-security-test"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("cart-security-test"));
    }

    @Test
    void anyAuthenticatedRoleCanReadOwnCart() throws Exception {
        mockMvc.perform(get("/api/cart").with(token("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void requestCannotSelectAnotherUserId() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .queryParam("userId", UUID.randomUUID().toString())
                        .with(token("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
    }

    @Test
    void invalidQuantityReturnsSafeValidationError() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(token("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\""
                                + UUID.randomUUID() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void clearIsIdempotentAndReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/cart").with(token("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/cart").with(token("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.REVOKED);

        mockMvc.perform(get("/api/cart").with(token("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(String role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(USER_ID.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "user@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of(role)));
    }

    private CartResponse emptyCart() {
        return new CartResponse(
                USER_ID,
                0,
                null,
                List.of(),
                0,
                0,
                new BigDecimal("0.00"),
                null,
                null,
                null);
    }

    @TestConfiguration
    static class SecurityBeans {

        @Bean
        Converter<Jwt, AbstractAuthenticationToken> cartJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
