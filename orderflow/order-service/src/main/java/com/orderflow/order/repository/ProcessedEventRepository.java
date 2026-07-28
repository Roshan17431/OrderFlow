package com.orderflow.order.repository;

import com.orderflow.order.domain.ProcessedEvent;
import com.orderflow.order.domain.ProcessedEventId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
