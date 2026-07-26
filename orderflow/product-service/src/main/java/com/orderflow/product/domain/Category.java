package com.orderflow.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120, unique = true)
    private String normalizedName;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CategoryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Category() {}

    public static Category create(
            UUID id,
            String name,
            String slug,
            String description,
            Instant now) {
        Category category = new Category();
        category.id = id;
        category.name = name;
        category.normalizedName = normalizeName(name);
        category.slug = slug;
        category.description = description;
        category.status = CategoryStatus.ACTIVE;
        category.createdAt = now;
        category.updatedAt = now;
        return category;
    }

    public void update(String name, String slug, String description, Instant now) {
        this.name = name;
        this.normalizedName = normalizeName(name);
        this.slug = slug;
        this.description = description;
        this.updatedAt = now;
    }

    public void activate(Instant now) {
        status = CategoryStatus.ACTIVE;
        updatedAt = now;
    }

    public void deactivate(Instant now) {
        status = CategoryStatus.INACTIVE;
        updatedAt = now;
    }

    private static String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public CategoryStatus getStatus() {
        return status;
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
