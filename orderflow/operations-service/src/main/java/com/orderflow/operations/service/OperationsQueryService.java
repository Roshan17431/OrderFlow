package com.orderflow.operations.service;

import com.orderflow.operations.config.OperationsProperties;
import com.orderflow.operations.dto.PageResponse;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsQueryService {
    private final JdbcTemplate jdbc;
    private final OperationsProperties properties;
    private final Clock clock;

    public OperationsQueryService(JdbcTemplate jdbc, OperationsProperties properties, Clock clock) {
        this.jdbc = jdbc; this.properties = properties; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        LocalDate date = LocalDate.now(clock);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("ordersCreated", count(date, "ORDERS_CREATED"));
        result.put("paymentsSucceeded", count(date, "PAYMENT_SUCCEEDED"));
        result.put("paymentsFailed", count(date, "PAYMENT_FAILED"));
        result.put("paymentVolume", amount(date, "PAYMENT_VOLUME"));
        result.put("inventoryReservationFailures", count(date, "INVENTORY_RESERVATION_FAILED"));
        result.put("deliveriesPendingAssignment", count(date, "DELIVERY_PENDING_ASSIGNMENT"));
        result.put("deliveriesCompleted", count(date, "DELIVERY_COMPLETED"));
        result.put("deliveriesFailed", count(date, "DELIVERY_FAILED"));
        result.put("notificationsFailed", count(date, "NOTIFICATION_FAILED"));
        result.put("openIncidents", jdbc.queryForObject("SELECT count(*) FROM operational_incident WHERE resolved=false", Long.class));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> reports(LocalDate from, LocalDate to, int page, int requestedSize, String sort) {
        int size = size(requestedSize);
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        long total = jdbc.queryForObject("SELECT count(*) FROM daily_report WHERE report_date BETWEEN ? AND ?",
                Long.class, Date.valueOf(start), Date.valueOf(end));
        String direction = "reportDate,asc".equalsIgnoreCase(sort) ? "ASC" : "DESC";
        List<Map<String, Object>> content = jdbc.queryForList("SELECT id,report_date AS \"reportDate\",status,generated_at AS \"generatedAt\",finalized_at AS \"finalizedAt\",orders_created AS \"ordersCreated\",payments_succeeded AS \"paymentsSucceeded\",payments_failed AS \"paymentsFailed\",payment_volume AS \"paymentVolume\",refund_volume AS \"refundVolume\",inventory_reservation_failures AS \"inventoryReservationFailures\",deliveries_completed AS \"deliveriesCompleted\",deliveries_failed AS \"deliveriesFailed\",notifications_delivered AS \"notificationsDelivered\",notifications_failed AS \"notificationsFailed\",version FROM daily_report WHERE report_date BETWEEN ? AND ? ORDER BY report_date " + direction + " LIMIT ? OFFSET ?",
                Date.valueOf(start), Date.valueOf(end), size, Math.max(0, page) * size);
        return page(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(LocalDate date) {
        List<Map<String, Object>> values = jdbc.queryForList("SELECT id,report_date AS \"reportDate\",status,generated_at AS \"generatedAt\",finalized_at AS \"finalizedAt\",orders_created AS \"ordersCreated\",payments_succeeded AS \"paymentsSucceeded\",payments_failed AS \"paymentsFailed\",payment_volume AS \"paymentVolume\",refund_volume AS \"refundVolume\",inventory_reservation_failures AS \"inventoryReservationFailures\",deliveries_completed AS \"deliveriesCompleted\",deliveries_failed AS \"deliveriesFailed\",notifications_delivered AS \"notificationsDelivered\",notifications_failed AS \"notificationsFailed\",version FROM daily_report WHERE report_date=?", Date.valueOf(date));
        return values.isEmpty() ? null : values.getFirst();
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> incidents(String type, String severity, Boolean resolved,
            Instant from, Instant to, int page, int requestedSize) {
        int size = size(requestedSize);
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now(clock) : to;
        String sql = " FROM operational_incident WHERE (? IS NULL OR incident_type=?) AND (? IS NULL OR severity=?) AND (? IS NULL OR resolved=?) AND occurred_at BETWEEN ? AND ?";
        Object[] args = {type, type, severity, severity, resolved, resolved,
                Timestamp.from(start), Timestamp.from(end)};
        long total = jdbc.queryForObject("SELECT count(*)" + sql, Long.class, args);
        Object[] pageArgs = {type, type, severity, severity, resolved, resolved,
                Timestamp.from(start), Timestamp.from(end), size, Math.max(0, page) * size};
        List<Map<String, Object>> content = jdbc.queryForList("SELECT *" + sql + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?", pageArgs);
        return page(content, page, size, total);
    }

    @Transactional
    public boolean resolve(UUID id, String note) {
        return jdbc.update("UPDATE operational_incident SET resolved=true,resolved_at=?,resolution_note=? WHERE id=? AND resolved=false",
                Timestamp.from(Instant.now(clock)), note.strip(), id) == 1;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> jobs() {
        return jdbc.queryForList("SELECT job_name AS \"jobName\",last_status AS \"lastStatus\",last_started_at AS \"lastStartedAt\",last_completed_at AS \"lastCompletedAt\",last_duration_ms AS \"lastDurationMs\",failure_count AS \"failureCount\" FROM scheduled_job_lock ORDER BY job_name");
    }

    private long count(LocalDate date, String metric) {
        Long value = jdbc.queryForObject("SELECT COALESCE(SUM(count_value),0) FROM daily_operational_metric WHERE metric_date=? AND metric_name=?",
                Long.class, Date.valueOf(date), metric);
        return value == null ? 0 : value;
    }

    private BigDecimal amount(LocalDate date, String metric) {
        BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(decimal_value),0) FROM daily_operational_metric WHERE metric_date=? AND metric_name=?",
                BigDecimal.class, Date.valueOf(date), metric);
        return value == null ? BigDecimal.ZERO : value;
    }

    private int size(int requested) { return Math.max(1, Math.min(properties.maxPageSize(), requested)); }
    private <T> PageResponse<T> page(List<T> content, int page, int size, long total) {
        return new PageResponse<>(content, Math.max(0, page), size, total, (int) Math.ceil((double) total / size));
    }
}
