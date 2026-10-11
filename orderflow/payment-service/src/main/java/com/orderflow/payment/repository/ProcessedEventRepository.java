package com.orderflow.payment.repository;

import com.orderflow.payment.domain.ProcessedEvent;
import com.orderflow.payment.domain.ProcessedEventId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
