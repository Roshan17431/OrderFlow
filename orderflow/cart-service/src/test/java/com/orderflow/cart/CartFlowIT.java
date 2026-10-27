package com.orderflow.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.error.CartDomainException;
import com.orderflow.cart.persistence.CartRedisRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@Testcontainers
class CartFlowIT {

    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ONE =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_TWO =
            UUID.fromString("50000000-0000-0000-0000-000000000002");
    private static final UUID PRODUCT_THREE =
            UUID.fromString("50000000-0000-0000-0000-000000000003");
    private static final UUID PRODUCT_FOUR =
            UUID.fromString("50000000-0000-0000-0000-000000000004");
    private static final String TOKEN_VERSION_KEY = "security:user:token-version:";
    private static final ProductStub PRODUCTS = ProductStub.start();

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.clients.product.base-url", PRODUCTS::baseUrl);
        registry.add("app.clients.product.discovery-enabled", () -> "false");
        registry.add("app.clients.product.resilience.max-attempts", () -> "2");
        registry.add("app.clients.product.resilience.retry-wait", () -> "1ms");
        registry.add("app.clients.product.resilience.sliding-window-size", () -> "20");
        registry.add("app.clients.product.resilience.minimum-calls", () -> "10");
        registry.add("app.cart.ttl", () -> "2m");
        registry.add("app.cart.max-distinct-items", () -> "2");
        registry.add("app.cart.max-quantity-per-item", () -> "5");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CartRedisRepository carts;

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set(TOKEN_VERSION_KEY + USER_ID, "0");
        PRODUCTS.reset();
        PRODUCTS.put(PRODUCT_ONE, "Keyboard", "10.00", "USD", true, 1L);
        PRODUCTS.put(PRODUCT_TWO, "Mouse", "3.50", "USD", true, 1L);
        PRODUCTS.put(PRODUCT_THREE, "Book", "7.00", "EUR", true, 1L);
        PRODUCTS.put(PRODUCT_FOUR, "Cable", "2.00", "USD", true, 1L);
    }

    @AfterAll
    static void stopProductStub() {
        PRODUCTS.stop();
    }

    @Test
    void readsOldDurableCartHashAndWritesRollbackCompatibleItems() throws Exception {
        var id = UUID.fromString("30000000-0000-0000-0000-000000000003");
        String item;
        try (var input = getClass().getResourceAsStream("/compat/boot3/StoredCartItem.json")) {
            item = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var hash = new java.util.HashMap<String, String>();
        hash.put("item:" + id, item);
        hash.put("__version", "7");
        hash.put("__currency", "USD");
        hash.put("__createdAt", "2026-01-02T03:04:05.123456Z");
        hash.put("__updatedAt", "2026-01-02T03:04:05.123456Z");
        hash.put("__expiresAt", java.time.Instant.now().plusSeconds(120).toString());
        redis.opsForHash().putAll(carts.key(USER_ID), hash);
        var existing = carts.find(USER_ID);
        assertThat(existing.version()).isEqualTo(7);
        assertThat(existing.items().getFirst().unitPriceSnapshot()).isEqualByComparingTo("41152263.041152");
        carts.update(USER_ID, existing.items().getFirst());
        String written = redis.opsForHash().get(carts.key(USER_ID), "item:" + id).toString();
        var old = new com.fasterxml.jackson.databind.ObjectMapper();
        var rollbackItem = old.readValue(written, com.orderflow.cart.persistence.StoredCartItem.class);
        assertThat(rollbackItem.quantity()).isEqualTo(3);
        assertThat(rollbackItem.unitPriceSnapshot()).isEqualTo("41152263.041152");
        assertThat(carts.find(USER_ID).version()).isEqualTo(8);
    }

    @Test
    void fullCartFlowEnforcesLimitsSnapshotsExpirationAndAtomicMutations()
            throws Exception {
        mockMvc.perform(get("/api/cart").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value(0.00));

        JsonNode added = json(mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_ONE, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.distinctItemCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(20.00))
                .andExpect(jsonPath("$.items[0].productNameSnapshot").value("Keyboard"))
                .andExpect(jsonPath("$.items[0].unitPriceSnapshot").value(10.00))
                .andReturn());
        String key = carts.key(USER_ID);
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isBetween(1L, 120L);
        assertThat(redis.opsForHash().get(key, "item:" + PRODUCT_ONE).toString())
                .contains("\"unitPriceSnapshot\":\"10.00\"")
                .doesNotContain("Bearer")
                .doesNotContain("Cookie");
        assertThat(added.get("userId").asText()).isEqualTo(USER_ID.toString());

        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_ONE, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.totalQuantity").value(4));
        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_ONE, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT_EXCEEDED"));

        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_TWO, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.distinctItemCount").value(2));
        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_THREE, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CURRENCY_MISMATCH"));
        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_FOUR, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_ITEM_LIMIT_EXCEEDED"));

        redis.expire(key, Duration.ofSeconds(10));
        mockMvc.perform(patch("/api/cart/items/{id}", PRODUCT_ONE)
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.totalQuantity").value(6));
        assertThat(redis.getExpire(key)).isGreaterThan(100L);

        mockMvc.perform(delete("/api/cart/items/{id}", PRODUCT_TWO).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(5))
                .andExpect(jsonPath("$.distinctItemCount").value(1));
        mockMvc.perform(delete("/api/cart/items/{id}", PRODUCT_ONE).with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(redis.hasKey(key)).isFalse();
        mockMvc.perform(delete("/api/cart").with(token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/cart").with(token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void validationUsesOneBatchCallAndPreservesChangedOrUnavailableItems()
            throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_ONE, 2)))
                .andExpect(status().isOk());
        PRODUCTS.resetCallCount();
        PRODUCTS.put(PRODUCT_ONE, "Keyboard", "12.00", "USD", true, 2L);

        mockMvc.perform(post("/api/cart/validate").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues.length()").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.cart.items[0].unitPriceSnapshot").value(12.00))
                .andExpect(jsonPath("$.cart.items[0].priceChanged").value(true))
                .andExpect(jsonPath("$.cart.version").value(2));
        assertThat(PRODUCTS.callCount()).isEqualTo(1);
        assertThat(PRODUCTS.lastRequestItemCount()).isEqualTo(1);

        PRODUCTS.put(PRODUCT_ONE, null, null, null, false, null);
        mockMvc.perform(post("/api/cart/validate").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.cart.items.length()").value(1))
                .andExpect(jsonPath("$.cart.items[0].purchasable").value(false))
                .andExpect(jsonPath("$.cart.version").value(3));
        assertThat(PRODUCTS.callCount()).isEqualTo(2);
    }

    @Test
    void downstreamHeadersAndFailuresAreHandledSafelyWithoutBreakingReads()
            throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("X-Correlation-ID", "cart-to-product-test")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_ONE, 1)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "cart-to-product-test"));
        assertThat(PRODUCTS.lastAuthorization()).startsWith("Bearer ");
        assertThat(PRODUCTS.lastCorrelationId()).isEqualTo("cart-to-product-test");

        PRODUCTS.setStatus(503);
        mockMvc.perform(post("/api/cart/validate")
                        .header("X-Correlation-ID", "temporary-product-failure")
                        .with(token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value("temporary-product-failure"));
        assertThat(PRODUCTS.callCount()).isGreaterThanOrEqualTo(3);

        mockMvc.perform(get("/api/cart").with(token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        PRODUCTS.setStatus(409);
        mockMvc.perform(post("/api/cart/items")
                        .with(token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(PRODUCT_TWO, 1)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
    }

    @Test
    void concurrentLuaMutationsPreserveLimitsCurrenciesAndUniqueVersions()
            throws Exception {
        List<Outcome> sameProduct = concurrently(5, index ->
                carts.add(USER_ID, item(PRODUCT_ONE, 1, "USD")));
        assertThat(sameProduct).allMatch(Outcome::successful);
        assertThat(sameProduct.stream().map(Outcome::version).toList())
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
        assertThat(carts.find(USER_ID).totalQuantity()).isEqualTo(5);

        List<Outcome> updates = concurrently(5, index ->
                carts.update(USER_ID, item(PRODUCT_ONE, index + 1, "USD")));
        assertThat(updates).allMatch(Outcome::successful);
        assertThat(new HashSet<>(updates.stream().map(Outcome::version).toList()))
                .hasSize(5);
        assertThat(carts.find(USER_ID).items().getFirst().quantity()).isBetween(1, 5);

        List<Outcome> overQuantity = concurrently(4, index ->
                carts.add(USER_ID, item(PRODUCT_ONE, 1, "USD")));
        assertThat(new HashSet<>(overQuantity.stream()
                        .filter(Outcome::successful)
                        .map(Outcome::version)
                        .toList()))
                .hasSize((int) overQuantity.stream().filter(Outcome::successful).count());
        assertThat(carts.find(USER_ID).items().getFirst().quantity()).isEqualTo(5);

        carts.clear(USER_ID);
        List<UUID> identifiers =
                List.of(PRODUCT_ONE, PRODUCT_TWO, PRODUCT_FOUR, UUID.randomUUID());
        List<Outcome> distinct = concurrently(4, index ->
                carts.add(USER_ID, item(identifiers.get(index), 1, "USD")));
        assertThat(distinct.stream().filter(Outcome::successful).count()).isEqualTo(2);
        assertThat(carts.find(USER_ID).distinctItemCount()).isEqualTo(2);

        carts.clear(USER_ID);
        List<Outcome> currencies = concurrently(2, index -> carts.add(
                USER_ID,
                item(index == 0 ? PRODUCT_ONE : PRODUCT_THREE, 1, index == 0 ? "USD" : "EUR")));
        assertThat(currencies.stream().filter(Outcome::successful).count()).isEqualTo(1);
        Cart finalCart = carts.find(USER_ID);
        assertThat(finalCart.distinctItemCount()).isEqualTo(1);
        assertThat(finalCart.items()).allMatch(item ->
                item.currency().equals(finalCart.currency()));
    }

    @Test
    void healthAndPrometheusExposeCartAndResilienceMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "orderflow_cart_mutations")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "resilience4j_circuitbreaker")));
    }

    private List<Outcome> concurrently(
            int operationCount, IndexedCartOperation operation) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(operationCount);
        ExecutorService executor = Executors.newFixedThreadPool(operationCount);
        try {
            List<Callable<Outcome>> tasks = new ArrayList<>();
            for (int index = 0; index < operationCount; index++) {
                int current = index;
                tasks.add(() -> {
                    barrier.await();
                    try {
                        return Outcome.success(operation.execute(current).version());
                    } catch (CartDomainException exception) {
                        return Outcome.failure(exception.code().name());
                    }
                });
            }
            List<Future<Outcome>> futures = executor.invokeAll(tasks);
            List<Outcome> results = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private CartItem item(UUID productId, int quantity, String currency) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new CartItem(
                productId,
                "Concurrent Product",
                quantity,
                new BigDecimal("1.00"),
                currency,
                1L,
                true,
                false,
                now,
                now);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token() {
        return jwt()
                .jwt(builder -> builder
                        .subject(USER_ID.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "customer@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of("CUSTOMER")));
    }

    private String addBody(UUID productId, int quantity) {
        return "{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @FunctionalInterface
    private interface IndexedCartOperation {
        Cart execute(int index);
    }

    private record Outcome(boolean successful, long version, String errorCode) {

        static Outcome success(long version) {
            return new Outcome(true, version, null);
        }

        static Outcome failure(String errorCode) {
            return new Outcome(false, -1, errorCode);
        }
    }

    private static final class ProductStub {

        private static final ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build();
        private final HttpServer server;
        private final Map<UUID, StubProduct> products = new ConcurrentHashMap<>();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile int status = 200;
        private volatile String lastAuthorization;
        private volatile String lastCorrelationId;
        private volatile int lastRequestItemCount;

        private ProductStub(HttpServer server) {
            this.server = server;
        }

        static ProductStub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                ProductStub stub = new ProductStub(server);
                server.createContext("/api/products/resolve", stub::handle);
                server.start();
                return stub;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to start product stub", exception);
            }
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        void reset() {
            products.clear();
            calls.set(0);
            status = 200;
            lastAuthorization = null;
            lastCorrelationId = null;
            lastRequestItemCount = 0;
        }

        void resetCallCount() {
            calls.set(0);
        }

        void put(
                UUID id,
                String name,
                String price,
                String currency,
                boolean purchasable,
                Long version) {
            products.put(
                    id,
                    new StubProduct(
                            name,
                            price == null ? null : new BigDecimal(price),
                            currency,
                            purchasable,
                            version));
        }

        void setStatus(int status) {
            this.status = status;
        }

        int callCount() {
            return calls.get();
        }

        String lastAuthorization() {
            return lastAuthorization;
        }

        String lastCorrelationId() {
            return lastCorrelationId;
        }

        int lastRequestItemCount() {
            return lastRequestItemCount;
        }

        private void handle(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            lastCorrelationId = exchange.getRequestHeaders().getFirst("X-Correlation-ID");
            if (status != 200) {
                respond(exchange, status, "{\"code\":\"DOWNSTREAM_TEST_ERROR\"}");
                return;
            }
            JsonNode request = JSON.readTree(exchange.getRequestBody());
            JsonNode productIds = request.get("productIds");
            lastRequestItemCount = productIds.size();
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode idValue : productIds) {
                UUID id = UUID.fromString(idValue.asText());
                StubProduct product = products.get(id);
                if (product == null || !product.purchasable()) {
                    items.add(Map.of(
                            "productId", id.toString(),
                            "purchasable", false));
                } else {
                    items.add(Map.of(
                            "productId", id.toString(),
                            "name", product.name(),
                            "priceAmount", product.price(),
                            "currency", product.currency(),
                            "purchasable", true,
                            "productVersion", product.version()));
                }
            }
            respond(exchange, 200, JSON.writeValueAsString(Map.of("items", items)));
        }

        private void respond(HttpExchange exchange, int responseStatus, String body)
                throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }

    private record StubProduct(
            String name,
            BigDecimal price,
            String currency,
            boolean purchasable,
            Long version) {}
}
