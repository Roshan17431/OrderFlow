package com.orderflow.order.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.events.*;
import com.orderflow.order.config.OutboxProperties;
import java.time.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class OutboxOrderingIT {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static OutboxStore store;
    static Instant now = Instant.parse("2026-10-11T00:00:00Z");
    @BeforeAll static void schema() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        jdbc = new JdbcTemplate(ds);
        // Exercise upgrade from the unchanged Phase 7 migrations with existing data.
        Flyway.configure().dataSource(ds).target("4").load().migrate();
        jdbc.update("insert into outbox_event(id,aggregate_type,aggregate_id,event_type,event_version,topic,message_key,payload,status,attempts,available_at,created_at) values (?, 'ORDER', ?, 'ORDER_CREATED', 1, 'order', 'legacy', '{}', 'NEW', 0, now(), now())", UUID.randomUUID(), UUID.randomUUID());
        Flyway.configure().dataSource(ds).load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where event_sequence > 0", Integer.class)).isEqualTo(1);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        store = new OutboxStore(jdbc, new ObjectMapper().findAndRegisterModules(), new OutboxProperties(Duration.ofSeconds(1), 50, 3, Duration.ofMinutes(2), Duration.ofSeconds(1), Duration.ofSeconds(5)), Clock.fixed(now, ZoneOffset.UTC));
    }
    @BeforeEach void reset() { jdbc.update("delete from outbox_event"); }
    @Test void secondPublisherCannotOvertakeInFlightOrRetryingEvent() {
        UUID key = UUID.randomUUID();
        UUID first = enqueue(key);
        UUID second = enqueue(key);
        var a = claim("a");
        assertThat(a).singleElement().satisfies(m -> assertThat(m.id()).isEqualTo(first));
        assertThat(claim("b")).isEmpty();
        store.failed(a.getFirst(), "a", "TEMPORARY");
        assertThat(claim("b")).isEmpty();
        jdbc.update("update outbox_event set available_at = ? where id = ?", java.sql.Timestamp.from(now), first);
        var retry = claim("b");
        assertThat(retry).singleElement().satisfies(m -> assertThat(m.id()).isEqualTo(first));
        store.published(first, "b");
        assertThat(claim("c")).singleElement().satisfies(m -> assertThat(m.id()).isEqualTo(second));
    }
    @Test void expiredClaimRecoversWithoutAllowingLaterEventsToPass() {
        UUID key = UUID.randomUUID();
        UUID first = enqueue(key);
        enqueue(key);
        claim("old");
        jdbc.update("update outbox_event set locked_at = ? where id = ?", java.sql.Timestamp.from(now.minusSeconds(121)), first);
        assertThat(claim("new")).singleElement().satisfies(m -> assertThat(m.id()).isEqualTo(first));
        store.published(first, "old");
        assertThat(jdbc.queryForObject("select status from outbox_event where id = ?", String.class, first)).isEqualTo("IN_PROGRESS");
    }
    @Test void unrelatedAggregatesRemainEligibleInTheSameBatch() {
        enqueue(UUID.randomUUID()); enqueue(UUID.randomUUID());
        assertThat(claim("a")).hasSize(2);
    }
    private java.util.List<OutboxMessage> claim(String owner) {
        return tx.execute(ignored -> store.claim(owner));
    }
    private UUID enqueue(UUID order) {
        var m = new EventMetadata(UUID.randomUUID(), EventType.ORDER_CANCEL_REQUESTED, 1, "ORDER", order, now, "ordering-test", null, "order-service");
        store.enqueue("order", order, new OrderCancelRequestedEvent(m, order, UUID.randomUUID(), "TEST"), m);
        return m.eventId();
    }
}
