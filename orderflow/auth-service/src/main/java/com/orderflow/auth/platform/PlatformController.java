package com.orderflow.auth.platform;

import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    private static final String SERVICE_NAME = "auth-service";
    private final PlatformMetadataService metadataService;

    public PlatformController(PlatformMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/ping")
    public PingResponse ping(HttpServletRequest request) {
        return new PingResponse(SERVICE_NAME, "UP", Instant.now(), correlationId(request));
    }

    @GetMapping("/info")
    public PlatformInfoResponse info(HttpServletRequest request) {
        PlatformMetadataSnapshot metadata = metadataService.getCurrentMetadata();
        return new PlatformInfoResponse(
                metadata.serviceName(),
                metadata.schemaVersion(),
                metadata.createdAt(),
                correlationId(request));
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID);
    }
}
