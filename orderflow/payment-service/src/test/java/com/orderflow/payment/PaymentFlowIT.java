package com.orderflow.payment;

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
import com.orderflow.events.PaymentRequestedEvent;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.repository.PaymentAttemptRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.payment.webhook.SimulatorWebhookVerifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentFlowIT {
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

    protected static final String ORDER_TOPIC = "orderflow.order-events.v1";
    protected static final UUID CUSTOMER_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000001");
    protected static final UUID OTHER_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000002");
    protected static final UUID ADMIN_ID =
            UUID.fromString("81000000-0000-0000-0000-000000000003");

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withInitScripts(Boolean.getBoolean("orderflow.verifyBoot3Schema") ? java.util.List.of("compat/boot3-schema.sql") : java.util.List.of())
                    .withDatabaseName("orderflow_payment_test")
                    .withUsername("orderflow_payment")
                    .withPassword("payment_test_password");
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));

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
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected KafkaTemplate<String, String> kafka;
    @Autowired protected PaymentRepository payments;
    @Autowired protected PaymentAttemptRepository attempts;
    @Autowired protected RefundRepository refunds;
    @Autowired protected SimulatorWebhookVerifier webhookVerifier;

    @BeforeEach
    void reset() {
        verifyExistingBusinessDataBeforeReset();
        jdbc.update("delete from provider_webhook_event");
        jdbc.update("delete from processed_event");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from idempotency_record");
        jdbc.update("delete from payment_cancellation_tombstone");
        jdbc.update("delete from refund");
        jdbc.update("delete from payment_attempt");
        jdbc.update("delete from payment");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set("security:user:token-version:" + CUSTOMER_ID, "0");
        redis.opsForValue().set("security:user:token-version:" + OTHER_ID, "0");
        redis.opsForValue().set("security:user:token-version:" + ADMIN_ID, "0");
    }

    @Test
    void kafkaCreationConfirmationSignedWebhookRefundAndSecurityAreDurable()
            throws Exception {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                """
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in (
                    'payment','payment_attempt','refund','provider_webhook_event',
                    'idempotency_record','outbox_event','processed_event')
                """, Integer.class)).isEqualTo(7);

        UUID orderId = UUID.randomUUID();
        PaymentRequestedEvent requested = paymentRequested(orderId, CUSTOMER_ID, "49.99");
        publish(requested);
        publish(requested);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(payments.findByOrderId(orderId)).isPresent();
            assertThat(payments.count()).isEqualTo(1);
        });
        Payment payment = payments.findByOrderId(orderId).orElseThrow();

        mockMvc.perform(get("/api/payments/orders/{id}", orderId).with(customer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(49.99))
                .andExpect(jsonPath("$.provider").value("SIMULATOR"))
                .andExpect(jsonPath("$.providerPaymentId").doesNotExist());
        mockMvc.perform(get("/api/payments/orders/{id}", orderId).with(other()))
                .andExpect(status().isNotFound());

        String confirmBody = "{\"paymentMethodToken\":\"tok_success\"}";
        for (int request = 0; request < 2; request++) {
            mockMvc.perform(post("/api/payments/orders/{id}/confirm", orderId)
                            .with(customer())
                            .header("Idempotency-Key", "confirm-success")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmBody))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }
        assertThat(attempts.countByPaymentId(payment.getId())).isEqualTo(1);

        payment = payments.findById(payment.getId()).orElseThrow();
        webhook(
                "evt-payment-success",
                "{\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                        + payment.getProviderPaymentId() + "\"}",
                200);
        UUID paymentId = payment.getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(count(
                    "select count(*) from outbox_event where event_type = 'PAYMENT_SUCCEEDED'"
                            + " and status = 'PUBLISHED'"))
                    .isEqualTo(1);
        });
        webhook(
                "evt-payment-success",
                "{\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                        + payment.getProviderPaymentId() + "\"}",
                200);
        assertThat(count("select count(*) from provider_webhook_event")).isEqualTo(1);
        webhook(
                "evt-payment-success",
                "{\"type\":\"payment.failed\",\"providerPaymentId\":\""
                        + payment.getProviderPaymentId() + "\"}",
                409);

        mockMvc.perform(post("/api/admin/payments/{id}/refunds", paymentId)
                        .with(admin())
                        .header("Idempotency-Key", "refund-partial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":19.99,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        Refund refund = refunds.findByPaymentId(
                        paymentId, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().getFirst();
        webhook(
                "evt-refund-success",
                "{\"type\":\"refund.succeeded\",\"providerRefundId\":\""
                        + refund.getProviderRefundId() + "\"}",
                200);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Payment updated = payments.findById(paymentId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(updated.getRefundedAmount()).isEqualByComparingTo("19.99");
            assertThat(count(
                    "select count(*) from outbox_event where event_type = 'PAYMENT_REFUNDED'"
                            + " and status = 'PUBLISHED'"))
                    .isEqualTo(1);
        });
    }

    @Test
    void declineCancellationAndInvalidWebhookProduceStableResults() throws Exception {
        UUID declinedOrder = UUID.randomUUID();
        publish(paymentRequested(declinedOrder, CUSTOMER_ID, "10.00"));
        awaitPayment(declinedOrder);
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", declinedOrder)
                        .with(customer())
                        .header("Idempotency-Key", "confirm-declined")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_declined\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(payments.findByOrderId(declinedOrder).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.FAILED));

        UUID cancelOrder = UUID.randomUUID();
        publish(paymentRequested(cancelOrder, CUSTOMER_ID, "11.00"));
        Payment cancelPayment = awaitPayment(cancelOrder);
        mockMvc.perform(post("/api/payments/{id}/cancel", cancelPayment.getId())
                        .with(customer())
                        .header("Idempotency-Key", "cancel-pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(count(
                        "select count(*) from outbox_event where event_type = 'PAYMENT_CANCELLED'"))
                        .isEqualTo(1));

        mockMvc.perform(post("/api/payments/webhooks/simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Payment-Timestamp", Instant.now().getEpochSecond())
                        .header("X-Payment-Event-ID", "invalid-signature")
                        .header("X-Payment-Signature", "00")
                        .content("{\"type\":\"payment.succeeded\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void concurrentIdenticalConfirmationsAndCancellationsRemainIdempotent()
            throws Exception {
        UUID confirmationOrder = UUID.randomUUID();
        publish(paymentRequested(confirmationOrder, CUSTOMER_ID, "18.00"));
        Payment confirmationPayment = awaitPayment(confirmationOrder);

        CountDownLatch confirmationStart = new CountDownLatch(1);
        ExecutorService confirmationExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = confirmationExecutor.submit(() -> {
                confirmationStart.await();
                return confirmationStatus(confirmationOrder, "concurrent-confirm");
            });
            Future<Integer> second = confirmationExecutor.submit(() -> {
                confirmationStart.await();
                return confirmationStatus(confirmationOrder, "concurrent-confirm");
            });
            confirmationStart.countDown();
            assertThat(List.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)))
                    .containsOnly(202);
        } finally {
            confirmationExecutor.shutdownNow();
        }
        assertThat(attempts.countByPaymentId(confirmationPayment.getId())).isEqualTo(1);

        UUID cancellationOrder = UUID.randomUUID();
        publish(paymentRequested(cancellationOrder, CUSTOMER_ID, "19.00"));
        Payment cancellationPayment = awaitPayment(cancellationOrder);

        CountDownLatch cancellationStart = new CountDownLatch(1);
        ExecutorService cancellationExecutor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = cancellationExecutor.submit(() -> {
                cancellationStart.await();
                return cancellationStatus(cancellationPayment.getId(), "concurrent-cancel");
            });
            Future<Integer> second = cancellationExecutor.submit(() -> {
                cancellationStart.await();
                return cancellationStatus(cancellationPayment.getId(), "concurrent-cancel");
            });
            cancellationStart.countDown();
            assertThat(List.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)))
                    .containsOnly(200);
        } finally {
            cancellationExecutor.shutdownNow();
        }
        assertThat(payments.findById(cancellationPayment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);
        assertThat(count(
                "select count(*) from outbox_event"
                        + " where aggregate_id = '" + cancellationOrder + "'"
                        + " and event_type = 'PAYMENT_CANCELLED'"))
                .isEqualTo(1);
    }

    @Test
    void requiresActionTimeoutDeadlineRawCardCancellationWebhookAndDltAreSafe()
            throws Exception {
        UUID actionOrder = UUID.randomUUID();
        publish(paymentRequested(actionOrder, CUSTOMER_ID, "12.00"));
        Payment actionPayment = awaitPayment(actionOrder);
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", actionOrder)
                        .with(customer())
                        .header("Idempotency-Key", "requires-action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_requires_action\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUIRES_ACTION"));
        actionPayment = payments.findById(actionPayment.getId()).orElseThrow();
        webhook(
                "evt-requires-action-success",
                "{\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                        + actionPayment.getProviderPaymentId() + "\"}",
                200);
        UUID actionPaymentId = actionPayment.getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(payments.findById(actionPaymentId).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.SUCCEEDED));

        UUID timeoutOrder = UUID.randomUUID();
        publish(paymentRequested(timeoutOrder, CUSTOMER_ID, "13.00"));
        awaitPayment(timeoutOrder);
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", timeoutOrder)
                        .with(customer())
                        .header("Idempotency-Key", "provider-timeout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_timeout\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_PROVIDER_TIMEOUT"));

        UUID deadlineOrder = UUID.randomUUID();
        publish(paymentRequested(deadlineOrder, CUSTOMER_ID, "14.00"));
        Payment deadlinePayment = awaitPayment(deadlineOrder);
        jdbc.update(
                "update payment set payment_deadline = now() - interval '1 second'"
                        + " where id = ?",
                deadlinePayment.getId());
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", deadlineOrder)
                        .with(customer())
                        .header("Idempotency-Key", "expired-deadline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_DEADLINE_EXPIRED"));

        UUID rawCardOrder = UUID.randomUUID();
        publish(paymentRequested(rawCardOrder, CUSTOMER_ID, "15.00"));
        Payment rawCardPayment = awaitPayment(rawCardOrder);
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", rawCardOrder)
                        .with(customer())
                        .header("Idempotency-Key", "raw-card-rejected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"4242424242424242\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_METHOD_TOKEN_INVALID"));
        assertThat(attempts.countByPaymentId(rawCardPayment.getId())).isZero();
        assertThat(count(
                "select count(*) from information_schema.columns"
                        + " where table_schema = 'public'"
                        + " and column_name ilike '%token%'"))
                .isZero();

        UUID cancelWebhookOrder = UUID.randomUUID();
        publish(paymentRequested(cancelWebhookOrder, CUSTOMER_ID, "16.00"));
        Payment cancelWebhookPayment = awaitPayment(cancelWebhookOrder);
        mockMvc.perform(post(
                                "/api/payments/orders/{id}/confirm",
                                cancelWebhookOrder)
                        .with(customer())
                        .header("Idempotency-Key", "cancel-webhook-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andExpect(status().isAccepted());
        cancelWebhookPayment =
                payments.findById(cancelWebhookPayment.getId()).orElseThrow();
        jdbc.update(
                "update payment set status = 'CANCEL_REQUESTED' where id = ?",
                cancelWebhookPayment.getId());
        webhook(
                "evt-provider-cancelled",
                "{\"type\":\"payment.cancelled\",\"providerPaymentId\":\""
                        + cancelWebhookPayment.getProviderPaymentId() + "\"}",
                200);
        UUID cancelWebhookPaymentId = cancelWebhookPayment.getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(payments.findById(cancelWebhookPaymentId)
                            .orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.CANCELLED);
            assertThat(count(
                    "select count(*) from outbox_event"
                            + " where event_type = 'PAYMENT_CANCELLED'"
                            + " and aggregate_id = '" + cancelWebhookOrder + "'"))
                    .isEqualTo(1);
        });

        assertInvalidVersionReachesDlt();
    }

    protected PaymentRequestedEvent paymentRequested(
            UUID orderId, UUID customerId, String amount) {
        Instant expiry = Instant.now().plusSeconds(900);
        EventMetadata metadata = new EventMetadata(
                UUID.randomUUID(), EventType.PAYMENT_REQUESTED, 1,
                "ORDER", orderId, Instant.now(), "payment-it",
                UUID.randomUUID(), "order-service");
        return new PaymentRequestedEvent(
                metadata, orderId, customerId, new BigDecimal(amount), "USD",
                expiry.minusSeconds(60), UUID.randomUUID(), expiry);
    }

    protected void publish(PaymentRequestedEvent event) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> record =
                new ProducerRecord<>(ORDER_TOPIC, event.orderId().toString(), payload);
        record.headers().add(new RecordHeader(
                "eventType", EventType.PAYMENT_REQUESTED.name()
                        .getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventVersion", "1".getBytes(StandardCharsets.UTF_8)));
        kafka.send(record).get();
    }

    private void assertInvalidVersionReachesDlt() throws Exception {
        String dltTopic = "orderflow.order-events.v1.DLT";
        Properties configuration = new Properties();
        configuration.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());
        configuration.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-invalid-version-" + UUID.randomUUID());
        configuration.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuration.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        configuration.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(configuration)) {
            consumer.subscribe(List.of(dltTopic));
            consumer.poll(Duration.ofMillis(250));
            String key = UUID.randomUUID().toString();
            PaymentRequestedEvent event =
                    paymentRequested(UUID.randomUUID(), CUSTOMER_ID, "17.00");
            tools.jackson.databind.node.ObjectNode payload =
                    objectMapper.valueToTree(event);
            ((tools.jackson.databind.node.ObjectNode)
                            payload.get("metadata"))
                    .put("eventVersion", 99);
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(ORDER_TOPIC, key, payload.toString());
            record.headers().add(new RecordHeader(
                    "eventType",
                    EventType.PAYMENT_REQUESTED.name()
                            .getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader(
                    "eventVersion", "99".getBytes(StandardCharsets.UTF_8)));
            kafka.send(record).get();

            AtomicBoolean found = new AtomicBoolean();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received -> {
                    if (key.equals(received.key())) {
                        found.set(true);
                    }
                });
                return found.get();
            });
        }
    }

    protected Payment awaitPayment(UUID orderId) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(payments.findByOrderId(orderId)).isPresent());
        return payments.findByOrderId(orderId).orElseThrow();
    }

    protected void webhook(String eventId, String body, int expectedStatus) throws Exception {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = webhookVerifier.signature(timestamp, payload);
        MvcResult result = mockMvc.perform(post("/api/payments/webhooks/simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Payment-Timestamp", timestamp)
                        .header("X-Payment-Event-ID", eventId)
                        .header("X-Payment-Signature", signature)
                        .content(payload))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    }

    protected long count(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private int confirmationStatus(UUID orderId, String key) throws Exception {
        return mockMvc.perform(post("/api/payments/orders/{id}/confirm", orderId)
                        .with(customer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int cancellationStatus(UUID paymentId, String key) throws Exception {
        return mockMvc.perform(post("/api/payments/{id}/cancel", paymentId)
                        .with(customer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CUSTOMER_CANCELLED\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    protected org.springframework.test.web.servlet.request.RequestPostProcessor customer() {
        return token(CUSTOMER_ID, "CUSTOMER");
    }
    protected org.springframework.test.web.servlet.request.RequestPostProcessor other() {
        return token(OTHER_ID, "CUSTOMER");
    }
    protected org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return token(ADMIN_ID, "ADMIN");
    }
    protected org.springframework.test.web.servlet.request.RequestPostProcessor token(
            UUID subject, String role) {
        return jwt().jwt(token -> token
                        .subject(subject.toString())
                        .claim("jti", "jti-" + subject)
                        .claim("token_version", 0)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final java.util.concurrent.atomic.AtomicBoolean EXISTING_DATA_CHECKED = new java.util.concurrent.atomic.AtomicBoolean();
    @Autowired jakarta.persistence.EntityManagerFactory compatibilityEntityManagerFactory;
    private void verifyExistingBusinessDataBeforeReset() {
        if (Boolean.getBoolean("orderflow.verifyBoot3Schema") && EXISTING_DATA_CHECKED.compareAndSet(false, true)) {
            try (var manager = compatibilityEntityManagerFactory.createEntityManager()) {
                var existing = manager.find(com.orderflow.payment.domain.Payment.class, java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
                assertThat(existing).isNotNull();
            }
        }
    }
}
