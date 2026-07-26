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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiSecurityErrorWriter writer;
    private final SecurityAuditService audit;

    public RestAccessDeniedHandler(ApiSecurityErrorWriter writer, SecurityAuditService audit) {
        this.writer = writer;
        this.audit = audit;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        boolean csrf = accessDeniedException instanceof CsrfException;
        ErrorCode code = csrf
                ? ErrorCode.CSRF_TOKEN_INVALID
                : ErrorCode.INSUFFICIENT_PERMISSIONS;
        audit.record(new SecurityAuditEvent(
                SecurityAuditEvent.Event.ACCESS_DENIED,
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
                HttpStatus.FORBIDDEN,
                code,
                csrf
                        ? "The CSRF token is missing or invalid."
                        : "You do not have permission to perform this action.");
    }
}
