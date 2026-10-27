package com.orderflow.order.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.order.controller.AdminOrderController;
import com.orderflow.order.controller.OrderController;
import com.orderflow.order.correlation.CorrelationIdFilter;
import com.orderflow.order.dto.PageResponse;
import com.orderflow.order.service.OrderCancellationService;
import com.orderflow.order.service.OrderCreationService;
import com.orderflow.order.service.OrderQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({OrderController.class, AdminOrderController.class})
@Import({
    OrderSecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiSecurityErrorWriter.class,
    RevocationValidationFilter.class,
    CorrelationIdFilter.class,
    OrderSecurityMvcTest.JwtTestConfiguration.class
})
class OrderSecurityMvcTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCreationService creation;

    @MockitoBean
    private OrderCancellationService cancellation;

    @MockitoBean
    private OrderQueryService queries;

    @MockitoBean
    private RevocationStateService revocations;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void validSecurityState() {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        when(queries.customerOrders(any(), any(), any(), any(), any(), anyLongAsInt(), anyLongAsInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));
        when(queries.adminOrders(any(), any(), any(), any(), any(), anyLongAsInt(), anyLongAsInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));
    }

    @Test
    void missingTokenUsesApiErrorWithoutRedirect() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("X-Correlation-ID", "order-auth-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("order-auth-test"));
    }

    @Test
    void customerCanListOwnOrdersButCannotUseAdminSearch() throws Exception {
        mockMvc.perform(get("/api/orders").with(token("CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/orders").with(token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders").with(token("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void revokedStaleAndUnavailableSecurityStateFailClosed() throws Exception {
        for (RevocationStateService.Validation validation :
                List.of(
                        RevocationStateService.Validation.REVOKED,
                        RevocationStateService.Validation.STALE)) {
            when(revocations.validate(any(), any(), anyLong())).thenReturn(validation);
            mockMvc.perform(get("/api/orders").with(token("CUSTOMER")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        }
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.UNAVAILABLE);
        mockMvc.perform(get("/api/orders")
                        .with(token("CUSTOMER"))
                        .header("X-Correlation-ID", "order-security-state"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SECURITY_STATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("order-security-state"));
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

    private static int anyLongAsInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        Converter<Jwt, AbstractAuthenticationToken> orderJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
