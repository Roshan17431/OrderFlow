package com.orderflow.auth.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_metadata")
public class PlatformMetadata {

    @Id
    private UUID id;

    @Column(name = "service_name", nullable = false, unique = true, length = 100)
    private String serviceName;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlatformMetadata() {
    }

    public UUID getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
