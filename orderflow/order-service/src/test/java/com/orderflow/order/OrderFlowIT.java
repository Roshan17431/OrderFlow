package com.orderflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReleasedEvent;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservationFailure;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.InventoryReservedItem;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderFlowIT {
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


    private static final String SHIPPING = ",\"shippingAddress\":{\"recipientName\":\"Test Recipient\",\"addressLine1\":\"10 Example Street\",\"city\":\"Kochi\",\"stateOrRegion\":\"Kerala\",\"postalCode\":\"682001\",\"countryCode\":\"IN\"}";

    private static final String INVENTORY_TOPIC = "orderflow.inventory-events.v1";
    private static final String INVENTORY_DLT = "orderflow.inventory-events.v1.DLT";
    private static final UUID CUSTOMER_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID =
            UUID.fromString("72000000-0000-0000-0000-000000000001");
    private static final CartStub CART = CartStub.start(CUSTOMER_ID, PRODUCT_ID);

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withInitScripts(Boolean.getBoolean("orderflow.verifyBoot3Schema") ? java.util.List.of("compat/boot3-schema.sql") : java.util.List.of())
                    .withDatabaseName("orderflow_order_test")
                    .withUsername("orderflow_order")
                    .withPassword("order_test_password");

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
        registry.add("app.clients.cart.base-url", CART::baseUrl);
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
    private CustomerOrderRepository orders;

    @BeforeEach
    void resetState() {
        verifyExistingBusinessDataBeforeReset();
        jdbc.update("delete from processed_event");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from order_status_history");
        jdbc.update("delete from order_item");
        jdbc.update("delete from idempotency_record");
        jdbc.update("delete from customer_order");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set("security:user:token-version:" + CUSTOMER_ID, "0");
        CART.valid();
    }

    @AfterAll
    static void stopCartStub() {
        CART.stop();
    }

    @Test
    void orderCreationInventoryTransitionsAndCancellationUseKafkaAndOutbox()
            throws Exception {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from information_schema.tables
                        where table_schema = 'public'
                          and table_name in (
                            'customer_order', 'order_item', 'order_status_history',
                            'idempotency_record', 'outbox_event', 'processed_event')
                        """,
                        Integer.class))
                .isEqualTo(6);

        MvcResult creation = mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "create-order-it")
                        .header("X-Correlation-ID", "order-workflow-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":4" + SHIPPING + "}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.status").value("INVENTORY_PENDING"))
                .andExpect(jsonPath("$.cartVersion").value(5))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.50))
                .andExpect(jsonPath("$.items[0].lineTotal").value(37.50))
                .andExpect(jsonPath("$.subtotal").value(37.50))
                .andExpect(jsonPath("$.totalAmount").value(37.50))
                .andReturn();
        UUID orderId = UUID.fromString(json(creation).path("id").asText());
        assertThat(CART.lastAuthorization()).startsWith("Bearer ");
        assertThat(CART.lastCorrelationId()).isEqualTo("order-workflow-it");
        assertThat(count("select count(*) from customer_order")).isEqualTo(1);
        assertThat(count("select count(*) from order_item")).isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from order_status_history
                        where order_id = ? and new_status = 'INVENTORY_PENDING'
                        """,
                        orderId))
                .isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from outbox_event
                        where aggregate_id = ? and event_type = 'ORDER_CREATED'
                        """,
                        orderId))
                .isEqualTo(1);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(
                        """
                        select count(*) from outbox_event
                        where aggregate_id = ? and event_type = 'ORDER_CREATED'
                          and status = 'PUBLISHED'
                        """,
                        orderId))
                .isEqualTo(1));
        JsonNode orderCreatedPayload = payload(orderId, "ORDER_CREATED");
        assertThat(orderCreatedPayload.path("metadata").path("correlationId").asText())
                .isEqualTo("order-workflow-it");
        assertThat(orderCreatedPayload.path("items").path(0).path("quantity").asLong())
                .isEqualTo(3);
        assertThat(orderCreatedPayload.path("totalAmount").decimalValue())
                .isEqualByComparingTo("37.50");

        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "create-order-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":4" + SHIPPING + "}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
        assertThat(CART.callCount()).isEqualTo(2);
        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "create-order-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":5" + SHIPPING + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_IDEMPOTENCY_CONFLICT"));

        UUID reservationId = UUID.randomUUID();
        InventoryReservedEvent reserved = new InventoryReservedEvent(
                metadata(
                        UUID.randomUUID(),
                        EventType.INVENTORY_RESERVED,
                        orderId,
                        "inventory-success-it",
                        UUID.fromString(orderCreatedPayload
                                .path("metadata")
                                .path("eventId")
                                .asText()),
                        "inventory-service"),
                orderId,
                reservationId,
                Instant.now().plusSeconds(900),
                List.of(new InventoryReservedItem(PRODUCT_ID, 3)));
        publish(INVENTORY_TOPIC, orderId, EventType.INVENTORY_RESERVED, reserved);
        publish(INVENTORY_TOPIC, orderId, EventType.INVENTORY_RESERVED, reserved);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orders.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(count(
                            """
                            select count(*) from order_status_history
                            where order_id = ?
                            """,
                            orderId))
                    .isEqualTo(3);
            assertThat(count(
                            "select count(*) from processed_event where event_id = ?",
                            reserved.metadata().eventId()))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            """
                            select correlation_id from order_status_history
                            where order_id = ? and new_status = 'PAYMENT_PENDING'
                            """,
                            String.class,
                            orderId))
                    .isEqualTo("inventory-success-it");
        });

        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .with(customerToken())
                        .header("Idempotency-Key", "cancel-order-it")
                        .header("X-Correlation-ID", "order-cancel-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCEL_REQUESTED"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(
                        """
                        select count(*) from outbox_event
                        where aggregate_id = ?
                          and event_type = 'ORDER_CANCEL_REQUESTED'
                          and status = 'PUBLISHED'
                        """,
                        orderId))
                .isEqualTo(1));
        JsonNode cancellationPayload = payload(orderId, "ORDER_CANCEL_REQUESTED");
        assertThat(cancellationPayload.path("metadata").path("correlationId").asText())
                .isEqualTo("order-cancel-it");

        InventoryReleasedEvent released = new InventoryReleasedEvent(
                metadata(
                        UUID.randomUUID(),
                        EventType.INVENTORY_RELEASED,
                        orderId,
                        "inventory-release-it",
                        UUID.fromString(cancellationPayload
                                .path("metadata")
                                .path("eventId")
                                .asText()),
                        "inventory-service"),
                orderId,
                reservationId,
                Instant.now(),
                "ORDER_CANCELLED",
                true);
        publish(INVENTORY_TOPIC, orderId, EventType.INVENTORY_RELEASED, released);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orders.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.CANCELLED);
            assertThat(count(
                            """
                            select count(*) from processed_event
                            where event_id = ?
                            """,
                            released.metadata().eventId()))
                    .isEqualTo(1);
        });
        mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                        .with(customerToken())
                        .header("Idempotency-Key", "cancel-order-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(count(
                        """
                        select count(*) from outbox_event
                        where aggregate_id = ? and event_type = 'ORDER_CANCEL_REQUESTED'
                        """,
                        orderId))
                .isEqualTo(1);
    }

    @Test
    void concurrentIdempotentRequestsCreateExactlyOneOrder() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Callable<MvcResult>> calls = List.of(
                    () -> create("concurrent-order-it", 4),
                    () -> create("concurrent-order-it", 4));
            List<Future<MvcResult>> results = executor.invokeAll(calls);
            JsonNode first = json(results.get(0).get());
            JsonNode second = json(results.get(1).get());
            assertThat(results.get(0).get().getResponse().getStatus()).isEqualTo(202);
            assertThat(results.get(1).get().getResponse().getStatus()).isEqualTo(202);
            assertThat(first.path("id").asText()).isEqualTo(second.path("id").asText());
        }
        assertThat(count("select count(*) from customer_order")).isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from idempotency_record
                        where scope = 'CREATE_ORDER'
                        """))
                .isEqualTo(1);
        assertThat(count(
                        """
                        select count(*) from outbox_event
                        where event_type = 'ORDER_CREATED'
                        """))
                .isEqualTo(1);
    }

    @Test
    void staleOutboxClaimIsRecoveredAfterPublisherCrashWindow() throws Exception {
        UUID orderId = UUID.fromString(json(create("outbox-recovery-it", 4))
                .path("id")
                .asText());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "select status from outbox_event where aggregate_id = ?",
                        String.class,
                        orderId)).isEqualTo("PUBLISHED"));

        jdbc.update(
                """
                update outbox_event
                   set status='IN_PROGRESS', published_at=null,
                       locked_at=now() - interval '10 minutes',
                       locked_by='stopped-publisher-instance'
                 where aggregate_id=? and event_type='ORDER_CREATED'
                """,
                orderId);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                    "select status from outbox_event where aggregate_id = ?",
                    String.class,
                    orderId)).isEqualTo("PUBLISHED");
            assertThat(count(
                    "select count(*) from outbox_event where aggregate_id = ? and event_type='ORDER_CREATED'",
                    orderId)).isEqualTo(1);
        });
    }

    @Test
    void rejectedCartsAndDownstreamFailuresDoNotCreateOrders() throws Exception {
        CART.empty();
        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "empty-cart-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":4" + SHIPPING + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CART_EMPTY"));
        assertThat(count("select count(*) from customer_order")).isZero();

        CART.valid();
        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "version-conflict-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":3" + SHIPPING + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CART_VERSION_CONFLICT"));

        CART.invalid();
        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "invalid-cart-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":4" + SHIPPING + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CART_INVALID"));

        CART.unavailable();
        mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", "cart-unavailable-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":4" + SHIPPING + "}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CART_SERVICE_UNAVAILABLE"));
        assertThat(count("select count(*) from customer_order")).isZero();
        assertThat(count("select count(*) from idempotency_record")).isZero();
        assertThat(count("select count(*) from outbox_event")).isZero();
    }

    @Test
    void reservationFailureTransitionsOrderAndInvalidEventsReachDlt()
            throws Exception {
        UUID orderId = UUID.fromString(json(create("failure-order-it", 4))
                .path("id")
                .asText());
        InventoryReservationFailedEvent failed = new InventoryReservationFailedEvent(
                metadata(
                        UUID.randomUUID(),
                        EventType.INVENTORY_RESERVATION_FAILED,
                        orderId,
                        "inventory-failure-it",
                        null,
                        "inventory-service"),
                orderId,
                List.of(new InventoryReservationFailure(
                        PRODUCT_ID, 3, 0, "INSUFFICIENT_STOCK")));
        publish(
                INVENTORY_TOPIC,
                orderId,
                EventType.INVENTORY_RESERVATION_FAILED,
                failed);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orders.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.INVENTORY_REJECTED);
            assertThat(count(
                            "select count(*) from processed_event where event_id = ?",
                            failed.metadata().eventId()))
                    .isEqualTo(1);
        });

        try (KafkaConsumer<String, String> consumer = dltConsumer()) {
            consumer.subscribe(List.of(INVENTORY_DLT));
            consumer.poll(Duration.ofMillis(100));
            UUID invalidOrder = UUID.randomUUID();
            String invalid = """
                    {
                      "metadata":{
                        "eventId":"%s",
                        "eventType":"INVENTORY_RESERVED",
                        "eventVersion":99,
                        "aggregateType":"ORDER",
                        "aggregateId":"%s",
                        "occurredAt":"%s",
                        "correlationId":"unsupported-version-it",
                        "producer":"inventory-service"
                      },
                      "orderId":"%s",
                      "reservationId":"%s",
                      "expiresAt":"%s",
                      "items":[{"productId":"%s","quantity":1}]
                    }
                    """.formatted(
                    UUID.randomUUID(),
                    invalidOrder,
                    Instant.now(),
                    invalidOrder,
                    UUID.randomUUID(),
                    Instant.now().plusSeconds(900),
                    PRODUCT_ID);
            publishJson(
                    INVENTORY_TOPIC,
                    invalidOrder,
                    EventType.INVENTORY_RESERVED,
                    invalid);
            ConsumerRecord<String, String> record = awaitDlt(consumer);
            assertThat(record.key()).isEqualTo(invalidOrder.toString());
            assertThat(record.value()).contains("\"eventVersion\":99");
            assertThat(record.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
            assertThat(orders.findById(invalidOrder)).isEmpty();
        }
    }

    private MvcResult create(String key, long version) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .with(customerToken())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedCartVersion\":" + version + SHIPPING + "}"))
                .andReturn();
    }

    private RequestPostProcessor customerToken() {
        return jwt()
                .jwt(builder -> builder
                        .subject(CUSTOMER_ID.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "customer@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
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

    private KafkaConsumer<String, String> dltConsumer() {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-it-dlt-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> awaitDlt(KafkaConsumer<String, String> consumer) {
        AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
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

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static final class CartStub {

        private final HttpServer server;
        private final UUID customerId;
        private final UUID productId;
        private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.VALID);
        private final java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> correlationId = new AtomicReference<>();

        private CartStub(HttpServer server, UUID customerId, UUID productId) {
            this.server = server;
            this.customerId = customerId;
            this.productId = productId;
        }

        static CartStub start(UUID customerId, UUID productId) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                CartStub stub = new CartStub(server, customerId, productId);
                server.createContext("/api/cart", stub::respond);
                server.start();
                return stub;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to start cart stub", exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void valid() {
            mode.set(Mode.VALID);
            calls.set(0);
            authorization.set(null);
            correlationId.set(null);
        }

        void empty() {
            mode.set(Mode.EMPTY);
        }

        void invalid() {
            mode.set(Mode.INVALID);
        }

        void unavailable() {
            mode.set(Mode.UNAVAILABLE);
        }

        int callCount() {
            return calls.get();
        }

        String lastAuthorization() {
            return authorization.get();
        }

        String lastCorrelationId() {
            return correlationId.get();
        }

        void stop() {
            server.stop(0);
        }

        private void respond(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            correlationId.set(exchange.getRequestHeaders().getFirst("X-Correlation-ID"));
            Mode current = mode.get();
            if (current == Mode.UNAVAILABLE) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            boolean validation = exchange.getRequestURI().getPath().endsWith("/validate");
            String cart = cartJson(current, validation);
            String response = validation
                    ? "{\"cart\":" + cart + ",\"valid\":"
                            + (current == Mode.VALID || current == Mode.EMPTY)
                            + ",\"issues\":"
                            + (current == Mode.INVALID
                                    ? "[{\"code\":\"PRICE_CHANGED\",\"productId\":\""
                                            + productId
                                            + "\",\"message\":\"changed\"}]"
                                    : "[]")
                            + "}"
                    : cart;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        private String cartJson(Mode current, boolean validation) {
            String now = Instant.now().toString();
            String items = current == Mode.EMPTY
                    ? "[]"
                    : """
                      [{
                        "productId":"%s",
                        "productNameSnapshot":"Authoritative Product",
                        "quantity":3,
                        "unitPriceSnapshot":12.50,
                        "currency":"USD",
                        "productVersionSnapshot":9,
                        "purchasable":true,
                        "priceChanged":%s,
                        "addedAt":"%s",
                        "updatedAt":"%s"
                      }]
                      """.formatted(productId, current == Mode.INVALID, now, now);
            return """
                    {
                      "userId":"%s",
                      "version":%d,
                      "currency":"USD",
                      "items":%s,
                      "distinctItemCount":%d,
                      "totalQuantity":%d,
                      "subtotal":1.00,
                      "createdAt":"%s",
                      "updatedAt":"%s",
                      "expiresAt":"%s"
                    }
                    """.formatted(
                    customerId,
                    validation ? 5 : 4,
                    items,
                    current == Mode.EMPTY ? 0 : 1,
                    current == Mode.EMPTY ? 0 : 3,
                    now,
                    now,
                    Instant.now().plusSeconds(900));
        }

        private enum Mode {
            VALID,
            EMPTY,
            INVALID,
            UNAVAILABLE
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean EXISTING_DATA_CHECKED = new java.util.concurrent.atomic.AtomicBoolean();
    @Autowired jakarta.persistence.EntityManagerFactory compatibilityEntityManagerFactory;
    private void verifyExistingBusinessDataBeforeReset() {
        if (Boolean.getBoolean("orderflow.verifyBoot3Schema") && EXISTING_DATA_CHECKED.compareAndSet(false, true)) {
            try (var manager = compatibilityEntityManagerFactory.createEntityManager()) {
                var existing = manager.find(com.orderflow.order.domain.CustomerOrder.class, java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
                assertThat(existing).isNotNull();
            }
        }
    }
}
