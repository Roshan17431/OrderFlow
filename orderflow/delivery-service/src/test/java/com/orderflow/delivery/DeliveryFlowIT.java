package com.orderflow.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.delivery.domain.DeliveryAgentAvailability;
import com.orderflow.delivery.domain.DeliveryStatus;
import com.orderflow.delivery.repository.DeliveryAgentRepository;
import com.orderflow.delivery.repository.DeliveryRepository;
import com.orderflow.events.DeliveryRequestedEvent;
import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.ShippingAddressSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryFlowIT {
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

    private static final UUID CUSTOMER = UUID.fromString("86000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("86000000-0000-0000-0000-000000000002");
    private static final UUID AGENT = UUID.fromString("86000000-0000-0000-0000-000000000003");

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine").withInitScripts(Boolean.getBoolean("orderflow.verifyBoot3Schema") ? java.util.List.of("compat/boot3-schema.sql") : java.util.List.of())
            .withDatabaseName("orderflow_delivery_test")
            .withUsername("orderflow_delivery")
            .withPassword("delivery_test_password");
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));

    static {
        POSTGRES.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired DeliveryRepository deliveries;
    @Autowired DeliveryAgentRepository agents;
    @Autowired com.orderflow.delivery.service.DeliveryAssignmentService assignments;

    @BeforeEach
    void reset() {
        verifyExistingBusinessDataBeforeReset();
        jdbc.update("delete from delivery_cancellation");
        jdbc.update("delete from delivery_status_history");
        jdbc.update("delete from processed_event");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from delivery");
        jdbc.update("delete from delivery_agent_profile");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        for (UUID user : List.of(CUSTOMER, OTHER, AGENT)) {
            redis.opsForValue().set("security:user:token-version:" + user, "0");
        }
    }

    @Test
    void requestedEventAssignmentLifecycleIdempotencyAndOwnershipUseRealKafka() throws Exception {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(4);
        UUID orderId = UUID.randomUUID();
        DeliveryRequestedEvent requested = requested(orderId, "phase6-it");
        publish(requested);
        publish(requested);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(deliveries.findByOrderId(orderId)).isPresent();
            assertThat(deliveries.count()).isEqualTo(1);
            assertThat(deliveries.findByOrderId(orderId).orElseThrow().getStatus())
                    .isEqualTo(DeliveryStatus.PENDING_ASSIGNMENT);
        });
        UUID deliveryId = deliveries.findByOrderId(orderId).orElseThrow().getId();

        mockMvc.perform(get("/api/deliveries/orders/{id}", orderId).with(token(CUSTOMER, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ASSIGNMENT"))
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Test Recipient"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Kochi"))
                .andExpect(jsonPath("$.shippingAddress.addressLine1").doesNotExist());
        mockMvc.perform(get("/api/deliveries/orders/{id}", orderId).with(token(OTHER, "CUSTOMER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/delivery/agents/me/availability")
                        .with(token(AGENT, "DELIVERY_AGENT"))
                        .header("X-Correlation-ID", "phase6-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(deliveries.findById(deliveryId).orElseThrow().getStatus())
                    .isEqualTo(DeliveryStatus.ASSIGNED);
            assertThat(agents.findByUserId(AGENT).orElseThrow().getAvailability())
                    .isEqualTo(DeliveryAgentAvailability.BUSY);
            assertThat(count("select count(*) from outbox_event where event_type='DELIVERY_ASSIGNED' and status='PUBLISHED'"))
                    .isEqualTo(1);
        });

        transition(deliveryId, "pickup", "PICKED_UP");
        transition(deliveryId, "out-for-delivery", "OUT_FOR_DELIVERY");
        transition(deliveryId, "delivered", "DELIVERED");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(agents.findByUserId(AGENT).orElseThrow().getAvailability())
                    .isEqualTo(DeliveryAgentAvailability.AVAILABLE);
            assertThat(count("select count(*) from delivery_status_history where delivery_id='" + deliveryId + "'"))
                    .isEqualTo(5);
            assertThat(count("select count(*) from delivery_status_history where delivery_id='" + deliveryId
                    + "' and correlation_id='phase6-it' and source_event_id is not null"))
                    .isEqualTo(1);
        });
        mockMvc.perform(post("/api/delivery/agents/me/deliveries/{id}/pickup", deliveryId)
                        .with(token(AGENT, "DELIVERY_AGENT")))
                .andExpect(status().isConflict());
    }

    @Test
    void failedDeliveryReleasesAgentPublishesOnceAndCannotRegress() throws Exception {
        UUID orderId = UUID.randomUUID();
        publish(requested(orderId, "delivery-failure-it"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findByOrderId(orderId)).isPresent());
        UUID deliveryId = deliveries.findByOrderId(orderId).orElseThrow().getId();

        mockMvc.perform(put("/api/delivery/agents/me/availability")
                        .with(token(AGENT, "DELIVERY_AGENT"))
                        .header("X-Correlation-ID", "delivery-failure-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"availability\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(deliveries.findById(deliveryId).orElseThrow().getStatus())
                        .isEqualTo(DeliveryStatus.ASSIGNED));

        transition(deliveryId, "pickup", "PICKED_UP");
        mockMvc.perform(post("/api/delivery/agents/me/deliveries/{id}/failed", deliveryId)
                        .with(token(AGENT, "DELIVERY_AGENT"))
                        .header("X-Correlation-ID", "delivery-failure-it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureCode\":\"VEHICLE_PROBLEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(agents.findByUserId(AGENT).orElseThrow().getAvailability())
                    .isEqualTo(DeliveryAgentAvailability.AVAILABLE);
            assertThat(count("select count(*) from outbox_event where event_type='DELIVERY_FAILED' and status='PUBLISHED'"))
                    .isEqualTo(1);
        });

        mockMvc.perform(post("/api/delivery/agents/me/deliveries/{id}/failed", deliveryId)
                        .with(token(AGENT, "DELIVERY_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureCode\":\"VEHICLE_PROBLEM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(post("/api/delivery/agents/me/deliveries/{id}/out-for-delivery", deliveryId)
                        .with(token(AGENT, "DELIVERY_AGENT")))
                .andExpect(status().isConflict());
        assertThat(count("select count(*) from outbox_event where event_type='DELIVERY_FAILED'"))
                .isEqualTo(1);
    }

    @Test
    void cancellationBeforeCreationIsDurableAndPreventsAssignment() throws Exception {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        var cancelled = new com.orderflow.events.DeliveryCancelRequestedEvent(
                new EventMetadata(UUID.randomUUID(), EventType.DELIVERY_CANCEL_REQUESTED, 1,
                        "ORDER", orderId, now, "cancel-before-create", null, "order-service"),
                orderId, CUSTOMER, null, "FULL_REFUND", now);
        var record = new ProducerRecord<String, String>("orderflow.delivery-events.v1",
                orderId.toString(), mapper.writeValueAsString(cancelled));
        record.headers().add(new RecordHeader("eventType",
                EventType.DELIVERY_CANCEL_REQUESTED.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        kafka.send(record).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("select count(*) from delivery_cancellation where order_id = ?", Integer.class, orderId)).isEqualTo(1));
        publish(requested(orderId, "late-create"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(deliveries.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
            assertThat(deliveries.findByOrderId(orderId).orElseThrow().getDeliveryAgentId()).isNull();
            assertThat(jdbc.queryForObject("select count(*) from outbox_event where aggregate_id = ? and event_type = 'DELIVERY_CANCELLED'", Integer.class, orderId)).isEqualTo(1);
        });
    }

    @Test
    void concurrentAssignmentsCannotUseOneAgentForTwoDeliveries() throws Exception {
        UUID firstOrder = UUID.randomUUID(), secondOrder = UUID.randomUUID();
        publish(requested(firstOrder, "concurrent-assignment"));
        publish(requested(secondOrder, "concurrent-assignment"));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(deliveries.count()).isEqualTo(2));
        var agent = com.orderflow.delivery.domain.DeliveryAgentProfile.register(AGENT, Instant.now());
        agent.setUserAvailability(DeliveryAgentAvailability.AVAILABLE, Instant.now());
        agents.saveAndFlush(agent);
        UUID first = deliveries.findByOrderId(firstOrder).orElseThrow().getId();
        UUID second = deliveries.findByOrderId(secondOrder).orElseThrow().getId();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return assignments.autoAssign(first, "concurrent-assignment", null); });
            var b = pool.submit(() -> { start.await(); return assignments.autoAssign(second, "concurrent-assignment", null); });
            start.countDown();
            int assigned = (a.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertThat(assigned).isEqualTo(1);
        }
        assertThat(deliveries.countByStatus(DeliveryStatus.ASSIGNED)).isEqualTo(1);
        assertThat(deliveries.countByStatus(DeliveryStatus.PENDING_ASSIGNMENT)).isEqualTo(1);
        assertThat(agents.findByUserId(AGENT).orElseThrow().getAvailability()).isEqualTo(DeliveryAgentAvailability.BUSY);
    }

    private void transition(UUID id, String operation, String status) throws Exception {
        mockMvc.perform(post("/api/delivery/agents/me/deliveries/{id}/" + operation, id)
                        .with(token(AGENT, "DELIVERY_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private DeliveryRequestedEvent requested(UUID orderId, String correlation) {
        Instant now = Instant.now();
        return new DeliveryRequestedEvent(
                new EventMetadata(UUID.randomUUID(), EventType.DELIVERY_REQUESTED, 1,
                        "ORDER", orderId, now, correlation, UUID.randomUUID(), "order-service"),
                orderId, CUSTOMER,
                new ShippingAddressSnapshot("Test Recipient", "10 Test Street", null,
                        "Kochi", "Kerala", "682001", "IN", null), now);
    }

    private void publish(DeliveryRequestedEvent event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                "orderflow.delivery-events.v1", event.orderId().toString(), mapper.writeValueAsString(event));
        record.headers().add(new RecordHeader("eventType",
                EventType.DELIVERY_REQUESTED.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        kafka.send(record).get();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID user, String role) {
        return jwt().jwt(builder -> builder.subject(user.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", role.toLowerCase() + "@example.test")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean EXISTING_DATA_CHECKED = new java.util.concurrent.atomic.AtomicBoolean();
    @Autowired jakarta.persistence.EntityManagerFactory compatibilityEntityManagerFactory;
    private void verifyExistingBusinessDataBeforeReset() {
        if (Boolean.getBoolean("orderflow.verifyBoot3Schema") && EXISTING_DATA_CHECKED.compareAndSet(false, true)) {
            try (var manager = compatibilityEntityManagerFactory.createEntityManager()) {
                var existing = manager.find(com.orderflow.delivery.domain.Delivery.class, java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
                assertThat(existing).isNotNull();
            }
        }
    }
}
