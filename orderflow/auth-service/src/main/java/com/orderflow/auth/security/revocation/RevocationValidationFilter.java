package com.orderflow.auth.security.revocation;

import com.orderflow.auth.audit.SecurityAuditEvent;
import com.orderflow.auth.audit.SecurityAuditService;
import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import com.orderflow.auth.security.ApiSecurityErrorWriter;
import com.orderflow.platform.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RevocationValidationFilter extends OncePerRequestFilter {

    private static final Set<String> SKIPPED = Set.of(
            "/api/auth/csrf",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/oauth2/exchange",
            "/api/auth/.well-known/jwks.json",
            "/api/auth/logout",
            "/api/platform/ping",
            "/api/platform/info");
    private final RevocationStateService revocations;
    private final ApiSecurityErrorWriter writer;
    private final SecurityAuditService audit;

    public RevocationValidationFilter(
            RevocationStateService revocations,
            ApiSecurityErrorWriter writer,
            SecurityAuditService audit) {
        this.revocations = revocations;
        this.writer = writer;
        this.audit = audit;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isSkipped(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Number version = token.getToken().getClaim("token_version");
            RevocationStateService.Validation validation = revocations.validate(
                    token.getToken().getId(),
                    UUID.fromString(token.getToken().getSubject()),
                    version == null ? -1 : version.longValue());
            if (validation != RevocationStateService.Validation.VALID) {
                reject(request, response, validation);
                return;
            }
        } catch (RuntimeException exception) {
            writer.write(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.SECURITY_STATE_UNAVAILABLE,
                    "Security state is temporarily unavailable.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSkipped(String path) {
        return SKIPPED.contains(path)
                || path.startsWith("/actuator/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/");
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            RevocationStateService.Validation validation) throws IOException {
        ErrorCode code = validation == RevocationStateService.Validation.UNAVAILABLE
                ? ErrorCode.SECURITY_STATE_UNAVAILABLE
                : ErrorCode.TOKEN_REVOKED;
        HttpStatus status = validation == RevocationStateService.Validation.UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.UNAUTHORIZED;
        audit.record(new SecurityAuditEvent(
                SecurityAuditEvent.Event.TOKEN_REJECTED,
                Instant.now(),
                null,
                null,
                null,
                (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID),
                null,
                null,
                validation.name()));
        writer.write(
                request,
                response,
                status,
                code,
                status == HttpStatus.UNAUTHORIZED
                        ? "The access token has been revoked."
                        : "Security state is temporarily unavailable.");
    }
}
