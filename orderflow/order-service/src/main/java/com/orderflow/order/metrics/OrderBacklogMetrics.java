package com.orderflow.order.metrics;

import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.repository.CustomerOrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderBacklogMetrics {

    public OrderBacklogMetrics(CustomerOrderRepository orders, MeterRegistry metrics) {
        Gauge.builder(
                        "orderflow_orders_waiting_for_inventory",
                        orders,
                        repository -> repository.countByStatus(OrderStatus.INVENTORY_PENDING))
                .register(metrics);
        Gauge.builder(
                        "orderflow_orders_waiting_for_payment",
                        orders,
                        repository -> repository.countByStatus(OrderStatus.PAYMENT_PENDING))
                .register(metrics);
    }
}
