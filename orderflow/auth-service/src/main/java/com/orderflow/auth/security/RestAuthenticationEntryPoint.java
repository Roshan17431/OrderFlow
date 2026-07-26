package com.orderflow.auth.security;

import com.orderflow.auth.audit.SecurityAuditEvent;
import com.orderflow.auth.audit.SecurityAuditService;
import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import com.orderflow.platform.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiSecurityErrorWriter writer;
    private final SecurityAuditService audit;

    public RestAuthenticationEntryPoint(
            ApiSecurityErrorWriter writer,
            SecurityAuditService audit) {
        this.writer = writer;
        this.audit = audit;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        boolean bearerPresented = request.getHeader("Authorization") != null;
        ErrorCode code = bearerPresented
                ? ErrorCode.INVALID_TOKEN
                : ErrorCode.AUTHENTICATION_REQUIRED;
        audit.record(new SecurityAuditEvent(
                SecurityAuditEvent.Event.TOKEN_REJECTED,
                Instant.now(),
                null,
                null,
                null,
                (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID),
                null,
                null,
                code.name()));
        writer.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                code,
                bearerPresented
                        ? "The access token is invalid."
                        : "Authentication is required.");
    }
}
