package com.orderflow.auth.platform;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformMetadataRepository extends JpaRepository<PlatformMetadata, UUID> {

    Optional<PlatformMetadata> findByServiceName(String serviceName);
}
