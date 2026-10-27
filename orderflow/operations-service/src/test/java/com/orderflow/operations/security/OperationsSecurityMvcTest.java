package com.orderflow.operations.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.operations.controller.OperationsController;
import com.orderflow.operations.correlation.CorrelationIdFilter;
import com.orderflow.operations.service.OperationsQueryService;
import java.time.LocalDate;
import java.util.Map;
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

@WebMvcTest(OperationsController.class)
@Import({OperationsSecurityConfiguration.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ApiSecurityErrorWriter.class, RevocationValidationFilter.class,
        CorrelationIdFilter.class, OperationsSecurityMvcTest.JwtTestConfiguration.class})
class OperationsSecurityMvcTest {
    @jakarta.annotation.Resource private MockMvc mockMvc;
    @MockitoBean private OperationsQueryService queries;
    @MockitoBean private RevocationStateService revocations;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach void setup() {
        when(revocations.validate(any(), any(), anyLong())).thenReturn(RevocationStateService.Validation.VALID);
        when(queries.overview()).thenReturn(Map.of("date", LocalDate.of(2026, 8, 19)));
    }

    @Test void missingTokenUsesApiErrorAndCorrelation() throws Exception {
        mockMvc.perform(get("/api/admin/operations/overview").header("X-Correlation-ID", "operations-test"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("operations-test"));
    }

    @Test void customerIsForbiddenButAdminCanRead() throws Exception {
        mockMvc.perform(get("/api/admin/operations/overview").with(token("CUSTOMER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
        mockMvc.perform(get("/api/admin/operations/overview").with(token("ADMIN"))).andExpect(status().isOk());
    }

    @Test void revokedAdminIsRejected() throws Exception {
        when(revocations.validate(any(), any(), anyLong())).thenReturn(RevocationStateService.Validation.REVOKED);
        mockMvc.perform(get("/api/admin/operations/overview").with(token("ADMIN")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String role) {
        return jwt().jwt(value -> value.subject(UUID.randomUUID().toString()).claim("jti", "jti")
                .claim("token_version", 0).claim("roles", java.util.List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @TestConfiguration static class JwtTestConfiguration {
        @Bean Converter<Jwt, AbstractAuthenticationToken> operationsJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
