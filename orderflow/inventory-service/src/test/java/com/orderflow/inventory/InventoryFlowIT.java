package com.orderflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.OrderCancelRequestedEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.events.OrderCreatedItem;
import com.orderflow.inventory.domain.InventoryItem;
import com.orderflow.inventory.domain.ReservationStatus;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import com.orderflow.inventory.service.InventoryWorkflowService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InventoryFlowIT {

    private static final String ORDER_TOPIC = "orderflow.order-events.v1";
    private static final String ORDER_DLT = "orderflow.order-events.v1.DLT";
    private static final UUID VENDOR_ID =
            UUID.fromString("61000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID =
            UUID.fromString("62000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID =
            UUID.fromString("63000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PRODUCT_ID =
            UUID.fromString("63000000-0000-0000-0000-000000000002");
    private static final ProductStub PRODUCTS = ProductStub.start();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("orderflow_inventory_test")
                    .withUsername("orderflow_inventory")
                    .withPassword("inventory_test_password");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.clients.product.base-url", PRODUCTS::baseUrl);
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
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private InventoryItemRepository inventory;

    @Autowired
    private InventoryReservationRepository reservations;

    @Autowired
    private InventoryWorkflowService workflows;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from processed_event");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from inventory_reservation_item");
        jdbc.update("delete from inventory_reservation");
        jdbc.update("delete from inventory_adjustment");
        jdbc.update("delete from inventory_item");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set("security:user:token-version:" + VENDOR_ID, "0");
    }

    @AfterAll
    static void stopProductStub() {
        PRODUCTS.stop();
    }

    @Test
    void managementAndKafkaWorkflowAreTransactionalIdempotentAndTraceable()
            throws Exception {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from information_schema.tables
                        where table_schema = 'public'
                          and table_name in (
                            'inventory_item', 'inventory_adjustment',
                            'inventory_reservation', 'inventory_reservation_item',
                            'outbox_event', 'processed_event')
                        """,
                        Integer.class))
                .isEqualTo(6);

        mockMvc.perform(post("/api/inventory/products/{productId}/adjustments", PRODUCT_ID)
                        .with(vendorToken())
                        .header("X-Correlation-ID", "inventory-adjustment-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":10,"reason":"INITIAL_STOCK","reference":"it"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(10))
                .andExpect(jsonPath("$.reservedQuantity").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(10));
        mockMvc.perform(post("/api/inventory/products/{productId}/adjustments", PRODUCT_ID)
                        .with(vendorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":-2,"reason":"COUNT_CORRECTION"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHandQuantity").value(8));
        mockMvc.perform(get("/api/inventory/products/{productId}/adjustments", PRODUCT_ID)
                        .with(vendorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent created =
                createdEvent(UUID.randomUUID(), orderId, PRODUCT_ID, 7, "workflow-it");
        publish(ORDER_TOPIC, orderId, EventType.ORDER_CREATED, created);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(reservations.findByOrderId(orderId))
                    .get()
                    .extracting(value -> value.getStatus())
                    .isEqualTo(ReservationStatus.RESERVED);
            assertThat(inventory.findByProductId(PRODUCT_ID))
                    .get()
                    .satisfies(item -> {
                        assertThat(item.getOnHandQuantity()).isEqualTo(8);
                        assertThat(item.getReservedQuantity()).isEqualTo(7);
                        assertThat(item.availableQuantity()).isEqualTo(1);
                    });
            assertThat(count(
                            "select count(*) from processed_event where event_id = ?",
                            created.metadata().eventId()))
                    .isEqualTo(1);
            assertThat(count(
                            """
                            select count(*) from outbox_event
                            where aggregate_id = ? and event_type = 'INVENTORY_RESERVED'
                              and status = 'PUBLISHED'
                            """,
                            orderId))
                    .isEqualTo(1);
        });
        JsonNode reservedPayload = payload(orderId, "INVENTORY_RESERVED");
        assertThat(reservedPayload.path("metadata").path("correlationId").asText())
                .isEqualTo("workflow-it");
        assertThat(reservedPayload.path("metadata").path("causationId").asText())
                .isEqualTo(created.metadata().eventId().toString());

        publish(ORDER_TOPIC, orderId, EventType.ORDER_CREATED, created);
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(inventory.findByProductId(PRODUCT_ID)
                                    .orElseThrow()
                                    .getReservedQuantity())
                            .isEqualTo(7);
                    assertThat(count(
                                    """
                                    select count(*) from outbox_event
                                    where aggregate_id = ?
                                      and event_type = 'INVENTORY_RESERVED'
                                    """,
                                    orderId))
                            .isEqualTo(1);
                });

        mockMvc.perform(post("/api/inventory/products/{productId}/adjustments", PRODUCT_ID)
                        .with(vendorToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":-2,"reason":"UNSAFE_CORRECTION"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_BELOW_RESERVED"));

        OrderCancelRequestedEvent cancelled =
                cancelEvent(UUID.randomUUID(), orderId, "workflow-it");
        publish(ORDER_TOPIC, orderId, EventType.ORDER_CANCEL_REQUESTED, cancelled);
        publish(ORDER_TOPIC, orderId, EventType.ORDER_CANCEL_REQUESTED, cancelled);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(reservations.findByOrderId(orderId))
                    .get()
                    .extracting(value -> value.getStatus())
                    .isEqualTo(ReservationStatus.RELEASED);
            assertThat(inventory.findByProductId(PRODUCT_ID)
                            .orElseThrow()
                            .getReservedQuantity())
                    .isZero();
            assertThat(count(
                            """
                            select count(*) from outbox_event
                            where aggregate_id = ? and event_type = 'INVENTORY_RELEASED'
                              and status = 'PUBLISHED'
                            """,
                            orderId))
                    .isEqualTo(1);
            assertThat(count(
                            "select count(*) from processed_event where event_id = ?",
                            cancelled.metadata().eventId()))
                    .isEqualTo(1);
        });

        UUID failedOrderId = UUID.randomUUID();
        OrderCreatedEvent insufficient =
                createdEvent(UUID.randomUUID(), failedOrderId, PRODUCT_ID, 9, "failed-it");
        publish(ORDER_TOPIC, failedOrderId, EventType.ORDER_CREATED, insufficient);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(reservations.findByOrderId(failedOrderId))
                    .get()
                    .extracting(value -> value.getStatus())
                    .isEqualTo(ReservationStatus.REJECTED);
            assertThat(inventory.findByProductId(PRODUCT_ID)
                            .orElseThrow()
                            .getReservedQuantity())
                    .isZero();
            assertThat(count(
                            """
                            select count(*) from outbox_event
                            where aggregate_id = ?
                              and event_type = 'INVENTORY_RESERVATION_FAILED'
                              and status = 'PUBLISHED'
                            """,
                            failedOrderId))
                    .isEqualTo(1);
        });
    }

    @Test
    void concurrentReservationsNeverOversellAndRemainAllOrNothing()
            throws Exception {
        inventory.save(InventoryItem.create(PRODUCT_ID, 10, Instant.now()));
        OrderCreatedEvent first =
                createdEvent(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_ID, 7, "race-a");
        OrderCreatedEvent second =
                createdEvent(UUID.randomUUID(), UUID.randomUUID(), PRODUCT_ID, 7, "race-b");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Void>> tasks = List.of(
                    () -> {
                        workflows.reserve(first);
                        return null;
                    },
                    () -> {
                        workflows.reserve(second);
                        return null;
                    });
            List<Future<Void>> results = executor.invokeAll(tasks);
            for (Future<Void> result : results) {
                result.get();
            }
        }

        InventoryItem item = inventory.findByProductId(PRODUCT_ID).orElseThrow();
        assertThat(item.getOnHandQuantity()).isEqualTo(10);
        assertThat(item.getReservedQuantity()).isEqualTo(7);
        assertThat(item.availableQuantity()).isEqualTo(3);
        assertThat(count(
                        "select count(*) from inventory_reservation where status = 'RESERVED'"))
                .isEqualTo(1);
        assertThat(count(
                        "select count(*) from inventory_reservation where status = 'REJECTED'"))
                .isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from outbox_event
                        where event_type = 'INVENTORY_RESERVED'
                        """))
                .isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from outbox_event
                        where event_type = 'INVENTORY_RESERVATION_FAILED'
                        """))
                .isEqualTo(1);
    }

    @Test
    void invalidAndUnsupportedEventsAreRetriedThenPublishedToDlt() throws Exception {
        try (KafkaConsumer<String, String> consumer = dltConsumer()) {
            consumer.subscribe(List.of(ORDER_DLT));
            consumer.poll(Duration.ofMillis(100));

            UUID orderId = UUID.randomUUID();
            OrderCreatedEvent valid =
                    createdEvent(UUID.randomUUID(), orderId, PRODUCT_ID, 1, "invalid-version");
            JsonNode tree = objectMapper.valueToTree(valid);
            ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("metadata"))
                    .put("eventVersion", 99);
            publishJson(
                    ORDER_TOPIC,
                    orderId,
                    EventType.ORDER_CREATED,
                    objectMapper.writeValueAsString(tree));

            ConsumerRecord<String, String> record = awaitDlt(consumer);
            assertThat(record.key()).isEqualTo(orderId.toString());
            assertThat(record.value()).contains("\"eventVersion\":99");
            assertThat(record.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
            assertThat(reservations.findByOrderId(orderId)).isEmpty();
            assertThat(count("select count(*) from processed_event")).isZero();
        }
    }

    private ConsumerRecord<String, String> awaitDlt(KafkaConsumer<String, String> consumer) {
        java.util.concurrent.atomic.AtomicReference<ConsumerRecord<String, String>> found =
                new java.util.concurrent.atomic.AtomicReference<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            var records = consumer.poll(Duration.ofMillis(250));
            if (records.isEmpty()) {
                return false;
            }
            found.set(records.iterator().next());
            return true;
        });
        return found.get();
    }

    private KafkaConsumer<String, String> dltConsumer() {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "inventory-it-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private OrderCreatedEvent createdEvent(
            UUID eventId,
            UUID orderId,
            UUID productId,
            long quantity,
            String correlationId) {
        EventMetadata metadata = metadata(
                eventId, EventType.ORDER_CREATED, orderId, correlationId, null, "order-service");
        BigDecimal price = new BigDecimal("10.00");
        return new OrderCreatedEvent(
                metadata,
                orderId,
                CUSTOMER_ID,
                "USD",
                price.multiply(BigDecimal.valueOf(quantity)),
                List.of(new OrderCreatedItem(
                        productId,
                        "Test Product",
                        quantity,
                        price,
                        price.multiply(BigDecimal.valueOf(quantity)),
                        "USD",
                        1L)));
    }

    private OrderCancelRequestedEvent cancelEvent(
            UUID eventId, UUID orderId, String correlationId) {
        return new OrderCancelRequestedEvent(
                metadata(
                        eventId,
                        EventType.ORDER_CANCEL_REQUESTED,
                        orderId,
                        correlationId,
                        null,
                        "order-service"),
                orderId,
                CUSTOMER_ID,
                "CUSTOMER_REQUEST");
    }

    private EventMetadata metadata(
            UUID eventId,
            EventType eventType,
            UUID orderId,
            String correlationId,
            UUID causationId,
            String producer) {
        return new EventMetadata(
                eventId,
                eventType,
                1,
                "ORDER",
                orderId,
                Instant.now(),
                correlationId,
                causationId,
                producer);
    }

    private void publish(
            String topic, UUID orderId, EventType eventType, Object payload)
            throws Exception {
        publishJson(topic, orderId, eventType, objectMapper.writeValueAsString(payload));
    }

    private void publishJson(
            String topic, UUID orderId, EventType eventType, String payload)
            throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, orderId.toString(), payload);
        record.headers().add(new RecordHeader(
                "eventType", eventType.name().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
                "eventVersion", "1".getBytes(StandardCharsets.UTF_8)));
        kafka.send(record).get();
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private JsonNode payload(UUID orderId, String eventType) throws Exception {
        String value = jdbc.queryForObject(
                """
                select payload::text from outbox_event
                where aggregate_id = ? and event_type = ?
                order by created_at
                limit 1
                """,
                String.class,
                orderId,
                eventType);
        return objectMapper.readTree(value);
    }

    private RequestPostProcessor vendorToken() {
        return jwt()
                .jwt(builder -> builder
                        .subject(VENDOR_ID.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "vendor@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of("VENDOR")))
                .authorities(new SimpleGrantedAuthority("ROLE_VENDOR"));
    }

    private static final class ProductStub {

        private final HttpServer server;

        private ProductStub(HttpServer server) {
            this.server = server;
        }

        static ProductStub start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                server.createContext(
                        "/api/vendor/products/",
                        ProductStub::respond);
                server.start();
                return new ProductStub(server);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to start product stub", exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        private static void respond(HttpExchange exchange) throws IOException {
            String[] segments = exchange.getRequestURI().getPath().split("/");
            String productId = segments[segments.length - 2];
            byte[] body = ("""
                    {"productId":"%s","status":"ACTIVE","vendorId":"%s"}
                    """.formatted(productId, VENDOR_ID))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
