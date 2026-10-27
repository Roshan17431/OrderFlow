package com.orderflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.http.CorrelationHeaders;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "eureka.client.enabled=false",
            "spring.cloud.discovery.enabled=false",
            "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false"
        })
@Import(ApiGatewayApplicationTest.TestEndpointsConfiguration.class)
class ApiGatewayApplicationTest {

    private final WebTestClient client;

    @Autowired
    private RouteDefinitionLocator routeDefinitions;

    @Autowired
    ApiGatewayApplicationTest(@LocalServerPort int port) {
        this.client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void suppliedValidCorrelationIdIsPreservedAndForwarded() {
        client.get()
                .uri("/test/echo")
                .header(CorrelationHeaders.CORRELATION_ID, "known-id_42")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationHeaders.CORRELATION_ID, "known-id_42")
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo("known-id_42");
    }

    @Test
    void untrustedIdentityHeadersAreRemoved() {
        client.get()
                .uri("/test/identity")
                .header("X-User-ID", "forged-user")
                .header("X-Vendor-ID", "forged-vendor")
                .header("X-Roles", "ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.identityHeaders").isEqualTo("absent");
    }

    @Test
    void missingCorrelationIdIsGenerated() {
        client.get()
                .uri("/test/echo")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches(
                        CorrelationHeaders.CORRELATION_ID,
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty();
    }

    @Test
    void apiResponsesCarryRestrictiveSecurityHeadersWithoutLocalHsts() {
        client.get()
                .uri("/test/echo")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY")
                .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
                .expectHeader().valueEquals(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=()")
                .expectHeader().doesNotExist("Strict-Transport-Security");
    }

    @Test
    void oversizedRequestIsRejectedWithStandardApiError() {
        client.post()
                .uri("/test/body")
                .header("X-Correlation-ID", "oversized-request")
                .bodyValue("x".repeat(1_048_577))
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectHeader().valueEquals("X-Correlation-ID", "oversized-request")
                .expectBody()
                .jsonPath("$.code").isEqualTo("PAYLOAD_TOO_LARGE")
                .jsonPath("$.error").isEqualTo("Payload Too Large")
                .jsonPath("$.correlationId").isEqualTo("oversized-request");
    }

    @Test
    void invalidCorrelationIdIsReplaced() {
        client.get()
                .uri("/test/echo")
                .header(CorrelationHeaders.CORRELATION_ID, "invalid value")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(
                        CorrelationHeaders.CORRELATION_ID,
                        value -> assertThat(value).isNotEqualTo("invalid value"))
                .expectBody()
                .jsonPath("$.correlationId").value(
                        value -> assertThat(value).isNotEqualTo("invalid value"));
    }

    @Test
    void unknownRouteUsesStandardApiError() {
        client.get()
                .uri("/does-not-exist")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(CorrelationHeaders.CORRELATION_ID)
                .expectBody(ApiError.class)
                .value(error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
                    assertThat(error.code()).isEqualTo("RESOURCE_NOT_FOUND");
                    assertThat(error.correlationId()).isNotBlank();
                    assertThat(error.path()).isEqualTo("/does-not-exist");
                });
    }

    @Test
    void unexpectedExceptionDoesNotLeakInternalDetails() {
        client.get()
                .uri("/test/fail")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectHeader().exists(CorrelationHeaders.CORRELATION_ID)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.message").isEqualTo("An unexpected error occurred")
                .jsonPath("$").value(body ->
                        assertThat(body.toString()).doesNotContain("sensitive-database-detail"));
    }

    @Test
    void everyPublicApiRouteHasAnExplicitServiceContract() {
        var routes = routeDefinitions.getRouteDefinitions().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).extracting(route -> route.getId())
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "auth-oauth", "auth-platform", "auth-api", "security-api", "product-api", "cart-api",
                        "admin-order-api", "inventory-api", "payment-webhook-api",
                        "admin-payment-api", "payment-api", "order-api", "delivery-agent-api",
                        "admin-delivery-api", "delivery-api", "notification-api", "operations-api"));
        assertThat(routes).allSatisfy(route -> {
            assertThat(route.getUri().getScheme()).isEqualTo("lb");
            assertThat(route.getPredicates()).isNotEmpty();
            assertThat(route.getPredicates().getFirst().getName()).isEqualTo("Path");
        });
        assertThat(routes.stream().filter(route -> route.getId().equals("payment-webhook-api"))
                .findFirst().orElseThrow().getFilters())
                .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("RemoveRequestHeader"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpointsConfiguration {

        @Bean
        TestEndpoints testEndpoints() {
            return new TestEndpoints();
        }

        @Bean
        @Order(-1)
        SecurityWebFilterChain phaseOneTestSecurity(ServerHttpSecurity http) {
            return http
                    .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                            "/test/**",
                            "/does-not-exist"))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(authorize -> authorize.anyExchange().permitAll())
                    .build();
        }
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/test/echo")
        Mono<Map<String, String>> echo(
                @RequestHeader(CorrelationHeaders.CORRELATION_ID) String correlationId) {
            return Mono.just(Map.of("correlationId", correlationId));
        }

        @GetMapping("/test/fail")
        Mono<Void> fail() {
            return Mono.error(new IllegalStateException("sensitive-database-detail"));
        }

        @GetMapping("/test/identity")
        Mono<Map<String, String>> identity(
                @RequestHeader(value = "X-User-ID", required = false) String userId,
                @RequestHeader(value = "X-Vendor-ID", required = false) String vendorId,
                @RequestHeader(value = "X-Roles", required = false) String roles) {
            return Mono.just(Map.of(
                    "identityHeaders",
                    userId == null && vendorId == null && roles == null ? "absent" : "present"));
        }

        @PostMapping("/test/body")
        Mono<Map<String, Integer>> body(@RequestBody String body) {
            return Mono.just(Map.of("length", body.length()));
        }
    }
}
