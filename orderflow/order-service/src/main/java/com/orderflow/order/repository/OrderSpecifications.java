package com.orderflow.order.repository;

import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<CustomerOrder> filters(
            UUID customerId, OrderStatus status, Instant from, Instant to) {
        return (root, query, criteria) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (customerId != null) {
                predicates.add(criteria.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(criteria.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(criteria.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(criteria.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return criteria.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
