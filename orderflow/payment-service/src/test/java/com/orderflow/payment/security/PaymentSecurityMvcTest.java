package com.orderflow.payment.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.payment.controller.AdminPaymentController;
import com.orderflow.payment.controller.PaymentController;
import com.orderflow.payment.controller.SimulatorWebhookController;
import com.orderflow.payment.correlation.CorrelationIdFilter;
import com.orderflow.payment.dto.PageResponse;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.dto.RefundResponse;
import com.orderflow.payment.error.GlobalExceptionHandler;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.service.CommandResult;
import com.orderflow.payment.service.PaymentCommandService;
import com.orderflow.payment.service.PaymentQueryService;
import com.orderflow.payment.service.PaymentWebhookService;
import com.orderflow.payment.service.RefundCommandService;
import com.orderflow.payment.webhook.SimulatorWebhookVerifier;
import com.orderflow.platform.error.ErrorCode;
import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
    PaymentController.class,
    AdminPaymentController.class,
    SimulatorWebhookController.class
})
@Import({
    PaymentSecurityConfiguration.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    ApiSecurityErrorWriter.class,
    RevocationValidationFilter.class,
    CorrelationIdFilter.class,
    GlobalExceptionHandler.class,
    PaymentSecurityMvcTest.JwtTestConfiguration.class
})
class PaymentSecurityMvcTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @jakarta.annotation.Resource private MockMvc mockMvc;
    @MockitoBean private PaymentQueryService queries;
    @MockitoBean private PaymentCommandService commands;
    @MockitoBean private RefundCommandService refunds;
    @MockitoBean private SimulatorWebhookVerifier verifier;
    @MockitoBean private PaymentWebhookService webhooks;
    @MockitoBean private RevocationStateService revocations;
    @MockitoBean private JwtDecoder jwtDecoder;

    @BeforeEach
    void setup() {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
        PaymentResponse response = new PaymentResponse(
                PAYMENT_ID, ORDER_ID, com.orderflow.payment.domain.PaymentStatus.PENDING,
                new BigDecimal("10.00"), "USD", "SIMULATOR", BigDecimal.ZERO,
                null, Instant.now().plusSeconds(600), Instant.now(), Instant.now(),
                null, null, null);
        when(queries.byOrder(any(), any())).thenReturn(response);
        when(queries.byId(any(), any())).thenReturn(response);
        when(queries.attempts(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(queries.search(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(commands.confirm(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(CommandResult.success(response, 202));
        when(commands.confirm(any(), any(), isNull(), anyString(), anyString()))
                .thenThrow(new PaymentDomainException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.PAYMENT_IDEMPOTENCY_KEY_REQUIRED,
                        "Idempotency-Key is required."));
        RefundResponse refund = new RefundResponse(
                UUID.randomUUID(), PAYMENT_ID, BigDecimal.ONE, "USD",
                com.orderflow.payment.domain.RefundStatus.PROCESSING,
                "CUSTOMER_REQUEST", null, Instant.now(), Instant.now(),
                null, null);
        when(refunds.create(
                        any(), any(), anyString(), any(), anyString()))
                .thenReturn(CommandResult.success(refund, 202));
    }

    @Test
    void missingTokenUsesApiErrorAndCustomerCannotUseAdminRefund() throws Exception {
        mockMvc.perform(get("/api/payments/orders/{id}", ORDER_ID)
                        .header("X-Correlation-ID", "payment-auth-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("payment-auth-test"));

        mockMvc.perform(post("/api/admin/payments/{id}/refunds", PAYMENT_ID)
                        .with(token("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerQueriesAndConfirmationRequireAuthenticationAndIdempotency() throws Exception {
        mockMvc.perform(get("/api/payments/orders/{id}", ORDER_ID)
                        .with(token("CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", ORDER_ID)
                        .with(token("CUSTOMER"))
                        .header("Idempotency-Key", "confirm-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", ORDER_ID)
                        .with(token("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void exactWebhookIsPublicButStillSignatureProtected() throws Exception {
        when(verifier.verify(any(), any(), any(), any()))
                .thenThrow(new PaymentDomainException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID,
                        "The webhook signature is invalid."));
        mockMvc.perform(post("/api/payments/webhooks/simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment.succeeded\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_WEBHOOK_SIGNATURE_INVALID"));

        mockMvc.perform(get("/api/payments/webhooks/simulator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void adminSearchRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/payments").with(token("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/payments").with(token("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/payments/{id}/refunds", PAYMENT_ID)
                        .with(token("ADMIN"))
                        .header("Idempotency-Key", "admin-refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void revokedStaleAndUnavailableSecurityStateFailClosed()
            throws Exception {
        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.REVOKED);
        mockMvc.perform(get("/api/payments/orders/{id}", ORDER_ID)
                        .with(token("CUSTOMER"))
                        .header("X-Correlation-ID", "revoked-payment-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"))
                .andExpect(jsonPath("$.correlationId")
                        .value("revoked-payment-token"));

        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.STALE);
        mockMvc.perform(get("/api/payments/orders/{id}", ORDER_ID)
                        .with(token("CUSTOMER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        when(revocations.validate(any(), any(), anyLong()))
                .thenReturn(RevocationStateService.Validation.UNAVAILABLE);
        mockMvc.perform(get("/api/payments/orders/{id}", ORDER_ID)
                        .with(token("CUSTOMER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("SECURITY_STATE_UNAVAILABLE"));
    }

    private org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String role) {
        return jwt().jwt(token -> token
                        .subject(USER_ID.toString())
                        .claim("jti", "test-jti")
                        .claim("token_version", 0)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @TestConfiguration
    static class JwtTestConfiguration {
        @Bean
        Converter<Jwt, AbstractAuthenticationToken> paymentJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
