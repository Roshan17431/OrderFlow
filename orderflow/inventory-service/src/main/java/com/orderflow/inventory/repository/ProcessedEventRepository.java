package com.orderflow.inventory.repository;

import com.orderflow.inventory.domain.ProcessedEvent;
import com.orderflow.inventory.domain.ProcessedEventId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEventId> {
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
