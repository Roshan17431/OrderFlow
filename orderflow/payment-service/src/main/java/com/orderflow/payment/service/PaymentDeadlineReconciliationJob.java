package com.orderflow.payment.service;

import com.orderflow.payment.config.PaymentReconciliationProperties;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.scheduler.JobResult;
import com.orderflow.scheduler.PostgresJobScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentDeadlineReconciliationJob {
    private final PaymentReconciliationProperties properties;
    private final PaymentRepository payments;
    private final PaymentReconciliationService reconciliation;
    private final PostgresJobScheduler scheduler;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final AtomicLong overdue = new AtomicLong();
    public PaymentDeadlineReconciliationJob(PaymentReconciliationProperties properties,
            PaymentRepository payments, PaymentReconciliationService reconciliation,
            PostgresJobScheduler scheduler, MeterRegistry metrics, Clock clock) {
        this.properties = properties; this.payments = payments; this.reconciliation = reconciliation;
        this.scheduler = scheduler; this.metrics = metrics; this.clock = clock;
        metrics.gauge("payment_overdue_total", overdue);
    }

    @Scheduled(fixedDelayString = "${app.payment.reconciliation.delay:30s}",
            initialDelayString = "${app.payment.reconciliation.delay:30s}")
    public void reconcileDeadlines() {
        if (!properties.enabled()) return;
        scheduler.execute("paymentDeadlineReconciliationJob", properties.lockDuration(), correlation -> {
            metrics.counter("payment_reconciliation_runs_total").increment();
            Instant now = Instant.now(clock);
            Set<PaymentStatus> states = Set.of(PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION, PaymentStatus.PROCESSING);
            overdue.set(payments.countByPaymentDeadlineBeforeAndStatusIn(now, states));
            long processed = 0, failures = 0;
            for (var id : payments.findOverdueIds(now, now.minus(properties.maxAge()), properties.batchSize())) {
                try {
                    var outcome = reconciliation.reconcile(id, correlation);
                    if (outcome == PaymentReconciliationService.Outcome.RECONCILED) {
                        processed++; metrics.counter("payment_reconciliation_processed_total").increment();
                    } else if (outcome == PaymentReconciliationService.Outcome.UNKNOWN) {
                        metrics.counter("payment_reconciliation_provider_unknown_total").increment();
                    }
                } catch (RuntimeException exception) {
                    failures++; metrics.counter("payment_reconciliation_failures_total").increment();
                }
            }
            overdue.set(payments.countByPaymentDeadlineBeforeAndStatusIn(Instant.now(clock), states));
            return new JobResult(processed, failures);
        });
    }
}
