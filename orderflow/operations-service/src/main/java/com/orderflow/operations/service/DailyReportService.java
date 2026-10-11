package com.orderflow.operations.service;

import com.orderflow.operations.config.OperationsProperties;
import com.orderflow.operations.scheduler.JobResult;
import com.orderflow.operations.scheduler.SchedulerLeaseService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyReportService {
    private final JdbcTemplate jdbc;
    private final OperationsProperties properties;
    private final SchedulerLeaseService leases;
    private final MeterRegistry metrics;
    private final Clock clock;

    public DailyReportService(JdbcTemplate jdbc, OperationsProperties properties,
            SchedulerLeaseService leases, MeterRegistry metrics, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.leases = leases;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.operations.reporting.cron}", zone = "${app.operations.reporting.zone:UTC}")
    public void finalizeDailyReports() {
        if (!properties.reporting().enabled()) return;
        leases.execute("dailyReportFinalizationJob", properties.reporting().lockDuration(), this::catchUp);
    }

    @Transactional
    public JobResult catchUp() {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneId.of(properties.reporting().zone()))).minusDays(1);
        LocalDate latest = jdbc.query("SELECT max(report_date) FROM daily_report",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
        LocalDate earliestMetric = jdbc.query("SELECT min(metric_date) FROM daily_operational_metric",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null);
        LocalDate cursor = latest == null ? (earliestMetric == null ? yesterday : earliestMetric) : latest.plusDays(1);
        List<LocalDate> dates = new ArrayList<>();
        while (!cursor.isAfter(yesterday) && dates.size() < properties.reporting().maxCatchupDays()) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        if (dates.isEmpty()) dates = List.of(yesterday);
        long failures = 0;
        for (LocalDate date : dates) {
            try {
                upsert(date);
                metrics.counter("operations_daily_reports_total", "result", "finalized").increment();
            } catch (RuntimeException exception) {
                failures++;
                metrics.counter("operations_report_generation_failures_total").increment();
            }
        }
        return new JobResult(dates.size() - failures, failures);
    }

    void upsert(LocalDate date) {
        long orders = count(date, "ORDERS_CREATED");
        long succeeded = count(date, "PAYMENT_SUCCEEDED");
        long paymentFailed = count(date, "PAYMENT_FAILED");
        BigDecimal paymentVolume = amount(date, "PAYMENT_VOLUME");
        BigDecimal refundVolume = amount(date, "REFUND_VOLUME");
        long inventoryFailed = count(date, "INVENTORY_RESERVATION_FAILED");
        long deliveriesCompleted = count(date, "DELIVERY_COMPLETED");
        long deliveriesFailed = count(date, "DELIVERY_FAILED");
        long notificationsSent = count(date, "NOTIFICATION_SENT");
        long notificationsFailed = count(date, "NOTIFICATION_FAILED");
        Instant now = Instant.now(clock);
        jdbc.update("""
                INSERT INTO daily_report(id,report_date,status,generated_at,finalized_at,
                  orders_created,payments_succeeded,payments_failed,payment_volume,refund_volume,
                  inventory_reservation_failures,deliveries_completed,deliveries_failed,
                  notifications_delivered,notifications_failed)
                VALUES (?,?,'FINALIZED',?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(report_date) DO UPDATE SET status='FINALIZED',generated_at=EXCLUDED.generated_at,
                  finalized_at=EXCLUDED.finalized_at,orders_created=EXCLUDED.orders_created,
                  payments_succeeded=EXCLUDED.payments_succeeded,payments_failed=EXCLUDED.payments_failed,
                  payment_volume=EXCLUDED.payment_volume,refund_volume=EXCLUDED.refund_volume,
                  inventory_reservation_failures=EXCLUDED.inventory_reservation_failures,
                  deliveries_completed=EXCLUDED.deliveries_completed,deliveries_failed=EXCLUDED.deliveries_failed,
                  notifications_delivered=EXCLUDED.notifications_delivered,
                  notifications_failed=EXCLUDED.notifications_failed,version=daily_report.version+1
                """, UUID.randomUUID(), Date.valueOf(date), Timestamp.from(now), Timestamp.from(now),
                orders, succeeded, paymentFailed,
                paymentVolume, refundVolume, inventoryFailed, deliveriesCompleted, deliveriesFailed,
                notificationsSent, notificationsFailed);
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
}
