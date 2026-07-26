package com.orderflow.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_products_vendor_sku", columnNames = {"vendor_id", "sku"}),
            @UniqueConstraint(name = "uq_products_slug", columnNames = "slug")
        })
public class Product {

    @Id
    private UUID id;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Product() {}

    public static Product draft(
            UUID id,
            UUID vendorId,
            String sku,
            String slug,
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            Category category,
            Instant now) {
        Product product = new Product();
        product.id = id;
        product.vendorId = vendorId;
        product.sku = sku;
        product.slug = slug;
        product.name = name;
        product.description = description;
        product.priceAmount = priceAmount;
        product.currency = currency;
        product.status = ProductStatus.DRAFT;
        product.category = category;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    public void update(
            String name,
            String description,
            BigDecimal priceAmount,
            String currency,
            Category category,
            Instant now) {
        requireNotArchived();
        this.name = name;
        this.description = description;
        this.priceAmount = priceAmount;
        this.currency = currency;
        this.category = category;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        requireNotArchived();
        status = ProductStatus.ACTIVE;
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        requireNotArchived();
        status = ProductStatus.INACTIVE;
        updatedAt = now;
    }

    public void archive(Instant now) {
        status = ProductStatus.ARCHIVED;
        updatedAt = now;
    }

    public boolean isPubliclyVisible() {
        return status == ProductStatus.ACTIVE
                && category.getStatus() == CategoryStatus.ACTIVE;
    }

    private void requireNotArchived() {
        if (status == ProductStatus.ARCHIVED) {
            throw new IllegalStateException("Archived products cannot be changed");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public String getSku() {
        return sku;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
