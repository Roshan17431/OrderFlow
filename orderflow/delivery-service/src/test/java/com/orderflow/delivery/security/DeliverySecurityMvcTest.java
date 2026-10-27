package com.orderflow.delivery.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.delivery.controller.AdminDeliveryController;
import com.orderflow.delivery.controller.DeliveryAgentController;
import com.orderflow.delivery.controller.DeliveryController;
import com.orderflow.delivery.correlation.CorrelationIdFilter;
import com.orderflow.delivery.domain.DeliveryAgentAvailability;
import com.orderflow.delivery.dto.AgentResponse;
import com.orderflow.delivery.dto.PageResponse;
import com.orderflow.delivery.error.DeliveryDomainException;
import com.orderflow.delivery.service.DeliveryAssignmentService;
import com.orderflow.delivery.service.DeliveryCommandService;
import com.orderflow.delivery.service.DeliveryQueryService;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({DeliveryController.class, DeliveryAgentController.class, AdminDeliveryController.class})
@Import({
    DeliverySecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiSecurityErrorWriter.class,
    RevocationValidationFilter.class,
    CorrelationIdFilter.class,
    DeliverySecurityMvcTest.JwtTestConfiguration.class
})
class DeliverySecurityMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @jakarta.annotation.Resource private MockMvc mockMvc;
    @MockitoBean private DeliveryAssignmentService assignment;
    @MockitoBean private DeliveryCommandService commands;
    @MockitoBean private DeliveryQueryService queries;
    @MockitoBean private RevocationStateService revocations;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void validSecurityState() {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        when(queries.agent(USER_ID)).thenReturn(new AgentResponse(
                USER_ID, DeliveryAgentAvailability.AVAILABLE, null, Instant.now()));
        when(queries.agentDeliveries(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(queries.history(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    }

    @Test
    void missingTokenUsesApiErrorCorrelationAndNoRedirect() throws Exception {
        mockMvc.perform(get("/api/deliveries/orders/" + UUID.randomUUID())
                        .header("X-Correlation-ID", "delivery-auth-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("delivery-auth-test"));
    }

    @Test
    void agentAndAdminRoutesEnforceRoles() throws Exception {
        mockMvc.perform(get("/api/delivery/agents/me").with(token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/delivery/agents/me").with(token("DELIVERY_AGENT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/deliveries/" + UUID.randomUUID() + "/history")
                        .with(token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/deliveries/" + UUID.randomUUID() + "/history")
                        .with(token("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void deliveryAgentCanManageOwnAvailabilityButCannotSelectBusy() throws Exception {
        mockMvc.perform(put("/api/delivery/agents/me/availability")
                        .with(token("DELIVERY_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        doThrow(new DeliveryDomainException(HttpStatus.CONFLICT,
                        com.orderflow.platform.error.ErrorCode.DELIVERY_AGENT_BUSY,
                        "BUSY is service-controlled"))
                .when(assignment).availability(eq(USER_ID), eq(DeliveryAgentAvailability.BUSY), any());
        mockMvc.perform(put("/api/delivery/agents/me/availability")
                        .with(token("DELIVERY_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"BUSY\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_AGENT_BUSY"));
    }

    @Test
    void revokedAndStaleTokensAreRejected() throws Exception {
        for (RevocationStateService.Validation result : List.of(
                RevocationStateService.Validation.REVOKED,
                RevocationStateService.Validation.STALE)) {
            when(revocations.validate(any(), any(), anyLong())).thenReturn(result);
            mockMvc.perform(get("/api/delivery/agents/me").with(token("DELIVERY_AGENT")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        }
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String role) {
        return jwt().jwt(token -> token.subject(USER_ID.toString())
                        .claim("jti", "delivery-test-jti")
                        .claim("token_version", 0)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void detailedAddressRequiresAgentRole() throws Exception {
        String path = "/api/delivery/agents/me/deliveries/" + UUID.randomUUID() + "/address";
        mockMvc.perform(get(path).with(token("CUSTOMER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(path).with(token("ADMIN"))).andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        Converter<Jwt, AbstractAuthenticationToken> deliveryJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
