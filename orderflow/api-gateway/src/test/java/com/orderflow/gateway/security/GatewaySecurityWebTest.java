package com.orderflow.gateway.security;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.orderflow.gateway.config.GatewaySecurityProperties;
import com.orderflow.gateway.correlation.CorrelationIdGlobalFilter;
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
@EnableConfigurationProperties(GatewaySecurityProperties.class)
@Import({
    GatewaySecurityConfiguration.class,
    GatewayAuthenticationEntryPoint.class,
    GatewayAccessDeniedHandler.class,
    GatewaySecurityErrorWriter.class,
    CorrelationIdGlobalFilter.class,
    GatewaySecurityWebTest.SecurityEndpoints.class
})
class GatewaySecurityWebTest {

    @Autowired
    private WebTestClient client;

    @MockBean
    private GatewayRevocationWebFilter revocationFilter;

    @BeforeEach
    void revocationStateIsCurrent() {
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
    }
}
