package com.orderflow.auth.platform;

import com.orderflow.auth.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformMetadataService {

    static final String SERVICE_NAME = "auth-service";
    private final PlatformMetadataRepository repository;

    public PlatformMetadataService(PlatformMetadataRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformMetadataSnapshot getCurrentMetadata() {
        PlatformMetadata metadata = repository.findByServiceName(SERVICE_NAME)
                .orElseThrow(() -> new ResourceNotFoundException("Platform metadata was not found"));
        return new PlatformMetadataSnapshot(
                metadata.getServiceName(),
                metadata.getSchemaVersion(),
                metadata.getCreatedAt());
    }
}
