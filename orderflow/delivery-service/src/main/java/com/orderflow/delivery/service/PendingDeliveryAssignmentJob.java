package com.orderflow.delivery.service;

import com.orderflow.delivery.config.DeliveryAssignmentJobProperties;
import com.orderflow.delivery.domain.DeliveryStatus;
import com.orderflow.delivery.repository.DeliveryRepository;
import com.orderflow.scheduler.JobResult;
import com.orderflow.scheduler.PostgresJobScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PendingDeliveryAssignmentJob {
    private final DeliveryAssignmentJobProperties properties;
    private final DeliveryRepository deliveries;
    private final DeliveryAssignmentService assignments;
    private final PostgresJobScheduler scheduler;
    private final MeterRegistry metrics;
    private final AtomicLong pending = new AtomicLong();

    public PendingDeliveryAssignmentJob(DeliveryAssignmentJobProperties properties,
            DeliveryRepository deliveries, DeliveryAssignmentService assignments,
            PostgresJobScheduler scheduler, MeterRegistry metrics) {
        this.properties = properties; this.deliveries = deliveries; this.assignments = assignments;
        this.scheduler = scheduler; this.metrics = metrics;
        metrics.gauge("deliveries_pending_assignment", pending);
    }

    @Scheduled(fixedDelayString = "${app.delivery.assignment-job.delay:30s}",
            initialDelayString = "${app.delivery.assignment-job.delay:30s}")
    public void assignPending() {
        if (!properties.enabled()) return;
        scheduler.execute("pendingDeliveryAssignmentJob", properties.lockDuration(), correlation -> {
            metrics.counter("delivery_assignment_job_runs_total").increment();
            pending.set(deliveries.countByStatus(DeliveryStatus.PENDING_ASSIGNMENT));
            long assigned = 0;
            long failures = 0;
            for (UUID id : deliveries.findPendingAssignmentIds(properties.batchSize())) {
                try {
                    if (!assignments.autoAssign(id, correlation, null)) {
                        metrics.counter("delivery_assignment_job_no_agent_total").increment();
                        break;
                    }
                    assigned++;
                    metrics.counter("delivery_assignment_job_assignments_total").increment();
                } catch (RuntimeException exception) {
                    failures++;
                    metrics.counter("delivery_assignment_job_failures_total").increment();
                }
            }
            pending.set(deliveries.countByStatus(DeliveryStatus.PENDING_ASSIGNMENT));
            return new JobResult(assigned, failures);
        });
    }
}
