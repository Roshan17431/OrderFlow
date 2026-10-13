package com.orderflow.gateway.security;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.orderflow.gateway.config.GatewaySecurityProperties;
import com.orderflow.gateway.config.GatewayRateLimitProperties;
import com.orderflow.gateway.config.GatewayHttpProperties;
import com.orderflow.gateway.correlation.CorrelationIdGlobalFilter;
import com.orderflow.gateway.ratelimit.RateLimitStore;
import com.orderflow.gateway.proxy.TrustedClientAddress;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = GatewaySecurityWebTest.SecurityEndpoints.class)
@EnableConfigurationProperties({
    GatewaySecurityProperties.class,
    GatewayRateLimitProperties.class,
    GatewayHttpProperties.class
})
@Import({
    GatewaySecurityConfiguration.class,
    GatewayAuthenticationEntryPoint.class,
    GatewayAccessDeniedHandler.class,
    GatewaySecurityErrorWriter.class,
    TrustedClientAddress.class,
    CorrelationIdGlobalFilter.class,
    GatewaySecurityWebTest.SecurityEndpoints.class
})
class GatewaySecurityWebTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private GatewayRevocationWebFilter revocationFilter;

    @MockBean
    private RateLimitStore rateLimitStore;

    @BeforeEach
    void revocationStateIsCurrent() {
        when(rateLimitStore.consume(any(), any(Integer.class), any(Duration.class)))
                .thenReturn(Mono.just(new RateLimitStore.Decision(
                        true, 100, Duration.ofMinutes(1))));
        when(revocationFilter.filter(any(), any())).thenAnswer(invocation -> {
            org.springframework.web.server.ServerWebExchange exchange =
                    invocation.getArgument(0);
            org.springframework.web.server.WebFilterChain chain =
                    invocation.getArgument(1);
            return chain.filter(exchange);
        });
    }

    @Test
    void registrationAndLoginRoutesArePublicWhenCsrfIsValid() {
        client.mutateWith(csrf())
                .post()
                .uri("/api/auth/register")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(csrf())
                .post()
                .uri("/api/auth/login")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void missingTokenUsesApiErrorCorrelationAndNeverRedirectsToLogin() {
        client.get()
                .uri("/api/security/customer")
                .header("X-Correlation-ID", "gateway-missing-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION)
                .expectHeader().valueEquals(
                        "X-Correlation-ID",
                        "gateway-missing-token")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED")
                .jsonPath("$.correlationId").isEqualTo("gateway-missing-token");
    }

    @Test
    void customerAndAdminRoleRulesAreEnforced() {
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/security/customer")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/security/admin")
                .header("X-Correlation-ID", "gateway-role-denied")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().valueEquals(
                        "X-Correlation-ID",
                        "gateway-role-denied")
                .expectBody()
                .jsonPath("$.code").isEqualTo("INSUFFICIENT_PERMISSIONS");
        client.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .subject("user-id")
                                .claim("jti", "admin-jti")
                                .claim("email", "admin@example.com")
                                .claim("roles", java.util.List.of("ADMIN"))
                                .claim("token_version", 0))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .get()
                .uri("/api/security/admin")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void catalogReadsArePublicButResolutionAndCartRequireAuthentication() {
        client.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.LOCATION);
        client.get()
                .uri("/api/categories")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(csrf())
                .post()
                .uri("/api/products/resolve")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
        client.get()
                .uri("/api/cart")
                .exchange()
                .expectStatus().isUnauthorized();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/cart")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void vendorAndAdminRoutesEnforceRoles() {
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/vendor/products")
                .exchange()
                .expectStatus().isForbidden();
        client.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .subject("vendor-id")
                                .claim("jti", "vendor-jti")
                                .claim("email", "vendor@example.com")
                                .claim("roles", java.util.List.of("VENDOR"))
                                .claim("token_version", 0))
                        .authorities(new SimpleGrantedAuthority("ROLE_VENDOR")))
                .get()
                .uri("/api/vendor/products")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/admin/categories")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void phaseFourOrderAndInventoryRolesAreEnforced() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/inventory/products/123").exchange()
                .expectStatus().isUnauthorized();
        client.mutateWith(customerJwt()).get().uri("/api/orders").exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/inventory/products/123")
                .exchange()
                .expectStatus().isForbidden();
        client.mutateWith(vendorJwt())
                .get()
                .uri("/api/inventory/products/123")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/admin/orders")
                .exchange()
                .expectStatus().isForbidden();
        client.mutateWith(adminJwt())
                .get()
                .uri("/api/admin/orders")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(vendorJwt())
                .get()
                .uri("/api/inventory/reservations/order/123")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void paymentWebhookIsPublicWithoutCsrfWhilePaymentAndRefundRoutesAreProtected() {
        client.post()
                .uri("/api/payments/webhooks/simulator")
                .exchange()
                .expectStatus().isOk();
        client.get()
                .uri("/api/payments/webhooks/simulator")
                .exchange()
                .expectStatus().isUnauthorized();
        client.get()
                .uri("/api/payments/orders/123")
                .exchange()
                .expectStatus().isUnauthorized();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/payments/orders/123")
                .exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt())
                .get()
                .uri("/api/admin/payments")
                .exchange()
                .expectStatus().isForbidden();
        client.mutateWith(adminJwt())
                .get()
                .uri("/api/admin/payments")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void phaseSixDeliveryAndNotificationRoutesAreProtected() {
        client.get().uri("/api/deliveries/123").exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/api/notifications").exchange()
                .expectStatus().isUnauthorized();
        client.mutateWith(customerJwt()).get().uri("/api/deliveries/123").exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt()).get().uri("/api/notifications").exchange()
                .expectStatus().isOk();
        client.mutateWith(customerJwt()).get().uri("/api/delivery/agents/me").exchange()
                .expectStatus().isForbidden();
        client.mutateWith(deliveryAgentJwt()).get().uri("/api/delivery/agents/me").exchange()
                .expectStatus().isOk();
        client.mutateWith(deliveryAgentJwt()).get().uri("/api/admin/deliveries/123/history").exchange()
                .expectStatus().isForbidden();
        client.mutateWith(adminJwt()).get().uri("/api/admin/deliveries/123/history").exchange()
                .expectStatus().isOk();
    }

    @Test
    void operationsRoutesRequireAdmin() {
        client.get().uri("/api/admin/operations/overview").exchange().expectStatus().isUnauthorized();
        client.mutateWith(customerJwt()).get().uri("/api/admin/operations/overview").exchange()
                .expectStatus().isForbidden();
        client.mutateWith(adminJwt()).get().uri("/api/admin/operations/overview").exchange()
                .expectStatus().isOk();
    }

    private org.springframework.security.test.web.reactive.server
            .SecurityMockServerConfigurers.JwtMutator customerJwt() {
        return mockJwt()
                .jwt(jwt -> jwt
                        .subject("user-id")
                        .claim("jti", "customer-jti")
                        .claim("email", "customer@example.com")
                        .claim("roles", java.util.List.of("CUSTOMER"))
                        .claim("token_version", 0))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private org.springframework.security.test.web.reactive.server
            .SecurityMockServerConfigurers.JwtMutator vendorJwt() {
        return mockJwt()
                .jwt(jwt -> jwt
                        .subject("vendor-id")
                        .claim("jti", "vendor-jti")
                        .claim("email", "vendor@example.com")
                        .claim("roles", java.util.List.of("VENDOR"))
                        .claim("token_version", 0))
                .authorities(new SimpleGrantedAuthority("ROLE_VENDOR"));
    }

    private org.springframework.security.test.web.reactive.server
            .SecurityMockServerConfigurers.JwtMutator adminJwt() {
        return mockJwt()
                .jwt(jwt -> jwt
                        .subject("admin-id")
                        .claim("jti", "admin-jti")
                        .claim("email", "admin@example.com")
                        .claim("roles", java.util.List.of("ADMIN"))
                        .claim("token_version", 0))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.security.test.web.reactive.server
            .SecurityMockServerConfigurers.JwtMutator deliveryAgentJwt() {
        return mockJwt()
                .jwt(jwt -> jwt
                        .subject("delivery-agent-id")
                        .claim("jti", "delivery-agent-jti")
                        .claim("email", "agent@example.com")
                        .claim("roles", java.util.List.of("DELIVERY_AGENT"))
                        .claim("token_version", 0))
                .authorities(new SimpleGrantedAuthority("ROLE_DELIVERY_AGENT"));
    }

    @RestController
    static class SecurityEndpoints {

        @PostMapping({"/api/auth/register", "/api/auth/login"})
        Mono<Map<String, String>> publicAuth() {
            return Mono.just(Map.of("status", "ok"));
        }

        @GetMapping("/api/security/customer")
        Mono<Map<String, String>> customer() {
            return Mono.just(Map.of("scope", "customer"));
        }

        @GetMapping("/api/security/admin")
        Mono<Map<String, String>> admin() {
            return Mono.just(Map.of("scope", "admin"));
        }

        @GetMapping({"/api/products", "/api/categories"})
        Mono<Map<String, String>> catalog() {
            return Mono.just(Map.of("scope", "catalog"));
        }

        @PostMapping("/api/products/resolve")
        Mono<Map<String, String>> resolve() {
            return Mono.just(Map.of("scope", "resolve"));
        }

        @GetMapping("/api/vendor/products")
        Mono<Map<String, String>> vendor() {
            return Mono.just(Map.of("scope", "vendor"));
        }

        @GetMapping("/api/admin/categories")
        Mono<Map<String, String>> categories() {
            return Mono.just(Map.of("scope", "admin-categories"));
        }

        @GetMapping("/api/cart")
        Mono<Map<String, String>> cart() {
            return Mono.just(Map.of("scope", "cart"));
        }

        @GetMapping("/api/orders")
        Mono<Map<String, String>> orders() {
            return Mono.just(Map.of("scope", "orders"));
        }

        @GetMapping({
            "/api/inventory/products/123",
            "/api/inventory/reservations/order/123"
        })
        Mono<Map<String, String>> inventory() {
            return Mono.just(Map.of("scope", "inventory"));
        }

        @GetMapping("/api/admin/orders")
        Mono<Map<String, String>> adminOrders() {
            return Mono.just(Map.of("scope", "admin-orders"));
        }

        @PostMapping("/api/payments/webhooks/simulator")
        Mono<Map<String, String>> paymentWebhook() {
            return Mono.just(Map.of("scope", "payment-webhook"));
        }

        @GetMapping("/api/payments/webhooks/simulator")
        Mono<Map<String, String>> paymentWebhookRead() {
            return Mono.just(Map.of("scope", "payment-webhook-read"));
        }

        @GetMapping("/api/payments/orders/123")
        Mono<Map<String, String>> payment() {
            return Mono.just(Map.of("scope", "payment"));
        }

        @GetMapping("/api/admin/payments")
        Mono<Map<String, String>> adminPayments() {
            return Mono.just(Map.of("scope", "admin-payments"));
        }

        @GetMapping({
            "/api/deliveries/123",
            "/api/delivery/agents/me",
            "/api/admin/deliveries/123/history",
            "/api/notifications"
            ,"/api/admin/operations/overview"
        })
        Mono<Map<String, String>> phaseSix() {
            return Mono.just(Map.of("scope", "phase-six"));
        }
    }
}
