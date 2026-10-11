package com.orderflow.notification.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.notification.controller.NotificationController;
import com.orderflow.notification.correlation.CorrelationIdFilter;
import com.orderflow.notification.dto.PageResponse;
import com.orderflow.notification.dto.PreferenceResponse;
import com.orderflow.notification.error.NotificationDomainException;
import com.orderflow.notification.service.NotificationQueryService;
import com.orderflow.notification.service.PreferenceService;
import com.orderflow.notification.service.SseRegistry;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({
    NotificationSecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiSecurityErrorWriter.class,
    RevocationValidationFilter.class,
    CorrelationIdFilter.class,
    NotificationSecurityMvcTest.JwtTestConfiguration.class
})
class NotificationSecurityMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();

    @jakarta.annotation.Resource private MockMvc mockMvc;
    @MockBean private NotificationQueryService queries;
    @MockBean private PreferenceService preferences;
    @MockBean private SseRegistry streams;
    @MockBean private RevocationStateService revocations;
    @MockBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void validSecurityState() {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        when(queries.list(any(), any(Integer.class), any(Integer.class), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(preferences.get(USER_ID)).thenReturn(new PreferenceResponse(
                true, "r***@example.test", false, null, java.time.Instant.now(), 0));
    }

    @Test
    void missingTokenUsesApiErrorCorrelationAndNoRedirect() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("X-Correlation-ID", "notification-auth-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("notification-auth-test"));
    }

    @Test
    void authenticatedUserCanOnlyInvokeUserScopedEndpoints() throws Exception {
        mockMvc.perform(get("/api/notifications").with(token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/preferences").with(token()))
                .andExpect(status().isOk());
        UUID missing = UUID.randomUUID();
        when(queries.read(USER_ID, missing)).thenThrow(new NotificationDomainException(
                HttpStatus.NOT_FOUND,
                com.orderflow.platform.error.ErrorCode.NOTIFICATION_NOT_FOUND,
                "Notification not found"));
        mockMvc.perform(patch("/api/notifications/" + missing + "/read")
                        .with(token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications/stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void revokedAndStaleTokensAreRejected() throws Exception {
        for (RevocationStateService.Validation result : List.of(
                RevocationStateService.Validation.REVOKED,
                RevocationStateService.Validation.STALE)) {
            when(revocations.validate(any(), any(), anyLong())).thenReturn(result);
            mockMvc.perform(get("/api/notifications").with(token()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        }
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token() {
        return jwt().jwt(token -> token.subject(USER_ID.toString())
                        .claim("jti", "notification-test-jti")
                        .claim("token_version", 0)
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        Converter<Jwt, AbstractAuthenticationToken> notificationJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
