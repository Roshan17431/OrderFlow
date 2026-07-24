package com.orderflow.auth.platform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.auth.correlation.CorrelationIdFilter;
import com.orderflow.auth.error.GlobalExceptionHandler;
import com.orderflow.platform.http.CorrelationHeaders;
import java.time.Instant;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PlatformControllerTest {

    private PlatformMetadataService metadataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        metadataService = mock(PlatformMetadataService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PlatformController(metadataService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(
                                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                .build()))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void pingReturnsServiceStateAndRequestCorrelationId() throws Exception {
        mockMvc.perform(get("/api/platform/ping")
                        .header(CorrelationHeaders.CORRELATION_ID, "controller-test"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        CorrelationHeaders.CORRELATION_ID,
                        "controller-test"))
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value("controller-test"));
    }

    @Test
    void infoMapsServiceLayerDtoWithoutExposingEntity() throws Exception {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(metadataService.getCurrentMetadata())
                .thenReturn(new PlatformMetadataSnapshot("auth-service", "1", createdAt));

        mockMvc.perform(get("/api/platform/info"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationHeaders.CORRELATION_ID))
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.schemaVersion").value("1"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
