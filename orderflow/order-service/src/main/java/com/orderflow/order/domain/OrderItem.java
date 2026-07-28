package com.orderflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "product_version")
    private Long productVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderItem() {}

    OrderItem(
            CustomerOrder order,
            UUID productId,
            String productName,
            long quantity,
            BigDecimal unitPrice,
            String currency,
            Long productVersion,
            Instant now) {
        if (quantity <= 0 || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Order item quantity or price is invalid");
        }
        this.id = UUID.randomUUID();
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.currency = currency;
        this.productVersion = productVersion;
        this.createdAt = now;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getProductVersion() {
        return productVersion;
    }
}
