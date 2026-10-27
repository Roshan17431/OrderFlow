package com.orderflow.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.product.cache.CatalogCaches;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@Testcontainers
class ProductCatalogIT {
    @Test
    void validatesFreshOrRestoredBoot3SchemaAndSyntheticData() throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "select count(*) from information_schema.tables where table_name='boot3_compatibility_probe'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(Boolean.getBoolean("orderflow.verifyBoot3Schema") ? 1 : 0);
            }
            if (Boolean.getBoolean("orderflow.verifyBoot3Schema")) {
                try (var result = statement.executeQuery(
                        "select payload::text, amount, occurred_at from boot3_compatibility_probe")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).contains("ORDER_CREATED", "123456789.123456");
                    assertThat(result.getBigDecimal(2)).isEqualByComparingTo("123456789.123456");
                    assertThat(result.getTimestamp(3).toInstant()).isEqualTo(java.time.Instant.parse("2026-01-02T03:04:05.123456Z"));
                }
            }
        }
    }



    private static final UUID VENDOR_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_VENDOR_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID CATEGORY_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withInitScripts(Boolean.getBoolean("orderflow.verifyBoot3Schema") ? java.util.List.of("compat/boot3-schema.sql") : java.util.List.of())
                    .withDatabaseName("orderflow_product_test")
                    .withUsername("orderflow_product")
                    .withPassword("orderflow_product_test_password");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CacheManager caches;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void resetState() {
        verifyExistingBusinessDataBeforeReset();
        jdbc.update("delete from products");
        jdbc.update("update categories set status = 'ACTIVE', version = 0");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        for (UUID userId : List.of(VENDOR_ID, OTHER_VENDOR_ID, ADMIN_ID)) {
            redis.opsForValue().set("security:user:token-version:" + userId, "0");
        }
    }

    @Test
    void fullCatalogFlowCoversLifecycleCachingSearchOwnershipAndBatchResolution()
            throws Exception {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(1);
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4));

        JsonNode draft = json(mockMvc.perform(post("/api/vendor/products")
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProduct("KEYBOARD-001", "wireless-keyboard")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());
        UUID productId = UUID.fromString(draft.get("id").asText());

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        JsonNode active = json(mockMvc.perform(patch(
                                "/api/vendor/products/{id}/activate", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn());
        long version = active.get("version").asLong();

        caches.getCache(CatalogCaches.PRODUCT_DETAIL).evict("id:" + productId);
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(49.99));
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        JsonNode updated = json(mockMvc.perform(put("/api/vendor/products/{id}", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProduct("54.99", version)))
                .andExpect(status().isOk())
                .andReturn());
        version = updated.get("version").asLong();
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(54.99));

        mockMvc.perform(put("/api/vendor/products/{id}", productId)
                        .with(token(OTHER_VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProduct("55.99", version)))
                .andExpect(status().isNotFound());

        JsonNode adminUpdated = json(mockMvc.perform(put(
                                "/api/vendor/products/{id}", productId)
                        .with(token(ADMIN_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProduct("60.00", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceAmount").value(60.00))
                .andReturn());
        version = adminUpdated.get("version").asLong();

        mockMvc.perform(patch("/api/admin/categories/{id}/deactivate", CATEGORY_ID)
                        .with(token(ADMIN_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        mockMvc.perform(post("/api/vendor/products")
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProduct("KEYBOARD-001", "another-keyboard")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/vendor/products")
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProduct("KEYBOARD-002", "wireless-keyboard")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_SLUG_ALREADY_EXISTS"));

        mockMvc.perform(put("/api/vendor/products/{id}", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateProduct("61.00", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));

        mockMvc.perform(get("/api/products")
                        .param("q", "keyboard")
                        .param("category", "electronics")
                        .param("minPrice", "50.00")
                        .param("maxPrice", "70.00")
                        .param("currency", "USD")
                        .param("sort", "price,desc")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(productId.toString()));
        mockMvc.perform(get("/api/products").param("size", "101"))
                .andExpect(status().isBadRequest());

        UUID missing = UUID.randomUUID();
        statistics.clear();
        mockMvc.perform(post("/api/products/resolve")
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\""
                                + productId + "\",\"" + missing + "\",\"" + productId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.items[1].purchasable").value(false));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        JsonNode inactive = json(mockMvc.perform(patch(
                                "/api/vendor/products/{id}/deactivate", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andReturn());
        version = inactive.get("version").asLong();
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/categories/{id}/deactivate", CATEGORY_ID)
                        .with(token(ADMIN_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        JsonNode archived = json(mockMvc.perform(patch(
                                "/api/vendor/products/{id}/archive", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andReturn());
        mockMvc.perform(patch("/api/vendor/products/{id}/activate", productId)
                        .with(token(VENDOR_ID, "VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + archived.get("version").asLong() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void healthAndPrometheusExposeCatalogMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "orderflow_catalog_search_requests")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "orderflow_catalog_cache_invalidations")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(
            UUID userId, String role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", role.toLowerCase() + "@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createProduct(String sku, String slug) {
        return """
                {
                  "sku":"%s",
                  "name":"Wireless Keyboard",
                  "description":"Compact wireless keyboard",
                  "priceAmount":49.99,
                  "currency":"USD",
                  "categoryId":"%s",
                  "slug":"%s"
                }
                """.formatted(sku, CATEGORY_ID, slug);
    }

    private String updateProduct(String price, long version) {
        return """
                {
                  "name":"Wireless Keyboard",
                  "description":"Updated compact keyboard",
                  "priceAmount":%s,
                  "currency":"USD",
                  "categoryId":"%s",
                  "version":%d
                }
                """.formatted(price, CATEGORY_ID, version);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean EXISTING_DATA_CHECKED = new java.util.concurrent.atomic.AtomicBoolean();
    @Autowired jakarta.persistence.EntityManagerFactory compatibilityEntityManagerFactory;
    private void verifyExistingBusinessDataBeforeReset() {
        if (Boolean.getBoolean("orderflow.verifyBoot3Schema") && EXISTING_DATA_CHECKED.compareAndSet(false, true)) {
            try (var manager = compatibilityEntityManagerFactory.createEntityManager()) {
                var existing = manager.find(com.orderflow.product.domain.Category.class, java.util.UUID.fromString("10000000-0000-0000-0000-000000000001"));
                assertThat(existing).isNotNull();
            }
        }
    }
}
