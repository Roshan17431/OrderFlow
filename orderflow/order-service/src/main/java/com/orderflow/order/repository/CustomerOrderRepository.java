package com.orderflow.order.repository;

import com.orderflow.order.domain.CustomerOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository
        extends JpaRepository<CustomerOrder, UUID>,
                JpaSpecificationExecutor<CustomerOrder> {

    Page<CustomerOrder> findByCustomerId(UUID customerId, Pageable pageable);

    long countByStatus(com.orderflow.order.domain.OrderStatus status);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select customerOrder from CustomerOrder customerOrder where customerOrder.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);
}
