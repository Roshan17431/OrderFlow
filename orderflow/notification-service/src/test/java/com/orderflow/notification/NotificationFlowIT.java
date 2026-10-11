package com.orderflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.DeliveryAssignedEvent;
import com.orderflow.events.DeliveryDeliveredEvent;
import com.orderflow.events.DeliveryOutForDeliveryEvent;
import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.PaymentSucceededEvent;
import com.orderflow.notification.domain.NotificationDeliveryStatus;
import com.orderflow.notification.repository.NotificationDeliveryRepository;
import com.orderflow.notification.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationFlowIT {
    private static final UUID USER = UUID.fromString("88000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("88000000-0000-0000-0000-000000000002");

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orderflow_notification_test")
            .withUsername("orderflow_notification")
            .withPassword("notification_test_password");
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
    @Autowired NotificationRepository notifications;
    @Autowired NotificationDeliveryRepository deliveries;

    @BeforeEach
    void reset() {
        jdbc.update("delete from processed_event");
        jdbc.update("delete from outbox_event");
        jdbc.update("delete from notification_delivery");
        jdbc.update("delete from notification");
        jdbc.update("delete from notification_preference");
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        redis.opsForValue().set("security:user:token-version:" + USER, "0");
        redis.opsForValue().set("security:user:token-version:" + OTHER, "0");
    }

    @Test
    void realKafkaCreatesDeduplicatedPersistentAndSimulatedEmailNotifications() throws Exception {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(3);
        mockMvc.perform(put("/api/notifications/preferences")
                        .with(token(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"emailAddress\":\"success@example.test\",\"smsEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true));

        UUID order = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        UUID agent = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentSucceededEvent payment = new PaymentSucceededEvent(
                metadata(EventType.PAYMENT_SUCCEEDED, order, "notification-it", null),
                UUID.randomUUID(), order, USER, "SIMULATOR", "sim_payment",
                new BigDecimal("49.99"), "USD", now);
        publish("orderflow.payment-events.v1", payment.metadata().eventType(), order, payment);
        publish("orderflow.payment-events.v1", payment.metadata().eventType(), order, payment);

        DeliveryAssignedEvent assigned = new DeliveryAssignedEvent(
                metadata(EventType.DELIVERY_ASSIGNED, order, "notification-it", payment.metadata().eventId()),
                delivery, order, USER, agent, now);
        DeliveryOutForDeliveryEvent out = new DeliveryOutForDeliveryEvent(
                metadata(EventType.DELIVERY_OUT_FOR_DELIVERY, order, "notification-it", assigned.metadata().eventId()),
                delivery, order, USER, agent, now.plusSeconds(1));
        DeliveryDeliveredEvent delivered = new DeliveryDeliveredEvent(
                metadata(EventType.DELIVERY_DELIVERED, order, "notification-it", out.metadata().eventId()),
                delivery, order, USER, agent, now.plusSeconds(2));
        publish("orderflow.delivery-events.v1", EventType.DELIVERY_ASSIGNED, order, assigned);
        publish("orderflow.delivery-events.v1", EventType.DELIVERY_OUT_FOR_DELIVERY, order, out);
        publish("orderflow.delivery-events.v1", EventType.DELIVERY_DELIVERED, order, delivered);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(notifications.count()).isEqualTo(4);
            assertThat(deliveries.count()).isEqualTo(4);
            assertThat(deliveries.findAll()).allMatch(d -> d.getStatus() == NotificationDeliveryStatus.SENT);
            assertThat(count("select count(*) from processed_event where consumer_name='notification-domain-events-v1'"))
                    .isEqualTo(4);
            assertThat(count("select count(*) from outbox_event where correlation_id='notification-it' and causation_id is not null"))
                    .isGreaterThanOrEqualTo(4);
        });

        mockMvc.perform(get("/api/notifications").with(token(USER)).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(get("/api/notifications").with(token(OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/notifications/unread-count").with(token(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));

        UUID first = notifications.findByUserId(USER, PageRequest.of(0, 1)).getContent().getFirst().getId();
        mockMvc.perform(patch("/api/notifications/{id}/read", first).with(token(OTHER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/{id}/read", first).with(token(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(patch("/api/notifications/{id}/read", first).with(token(USER)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/notifications/read-all").with(token(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    private EventMetadata metadata(EventType type, UUID aggregate, String correlation, UUID causation) {
        return new EventMetadata(UUID.randomUUID(), type, 1, "ORDER", aggregate,
                Instant.now(), correlation, causation, "integration-test");
    }

    private void publish(String topic, EventType type, UUID key, Object event) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key.toString(), mapper.writeValueAsString(event));
        record.headers().add(new RecordHeader("eventType",
                type.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        kafka.send(record).get();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID user) {
        return jwt().jwt(builder -> builder.subject(user.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "user@example.test")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
