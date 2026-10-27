package com.orderflow.operations.security;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.operations.correlation.CorrelationIdFilter;
import com.orderflow.platform.error.ApiError;
import com.orderflow.platform.error.ErrorCode;
import com.orderflow.platform.http.CorrelationHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrorWriter {
    private final ObjectMapper mapper;
    public ApiSecurityErrorWriter(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) return;
        String correlation = (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CorrelationHeaders.CORRELATION_ID, correlation);
        mapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), code.name(), message, request.getRequestURI(), correlation, List.of()));
    }
}
