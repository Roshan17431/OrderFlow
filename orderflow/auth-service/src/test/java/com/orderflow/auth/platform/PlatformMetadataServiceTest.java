package com.orderflow.auth.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.auth.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformMetadataServiceTest {

    private final PlatformMetadataRepository repository =
            mock(PlatformMetadataRepository.class);
    private final PlatformMetadataService service = new PlatformMetadataService(repository);

    @Test
    void returnsAControllerSafeSnapshot() {
        PlatformMetadata entity = mock(PlatformMetadata.class);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(entity.getServiceName()).thenReturn("auth-service");
        when(entity.getSchemaVersion()).thenReturn("1");
        when(entity.getCreatedAt()).thenReturn(createdAt);
        when(repository.findByServiceName("auth-service")).thenReturn(Optional.of(entity));

        PlatformMetadataSnapshot result = service.getCurrentMetadata();

        assertThat(result).isEqualTo(
                new PlatformMetadataSnapshot("auth-service", "1", createdAt));
    }

    @Test
    void reportsMissingMetadataAsResourceNotFound() {
        when(repository.findByServiceName("auth-service")).thenReturn(Optional.empty());

        assertThatThrownBy(service::getCurrentMetadata)
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Platform metadata was not found");
    }
}
