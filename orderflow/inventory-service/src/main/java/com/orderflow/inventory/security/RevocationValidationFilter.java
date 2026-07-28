package com.orderflow.inventory.security;

import com.orderflow.platform.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RevocationValidationFilter extends OncePerRequestFilter {

    private final RevocationStateService revocations;
    private final ApiSecurityErrorWriter writer;

    public RevocationValidationFilter(
            RevocationStateService revocations, ApiSecurityErrorWriter writer) {
        this.revocations = revocations;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }
        Number version = token.getToken().getClaim("token_version");
        RevocationStateService.Validation validation;
        try {
            validation = revocations.validate(
                    token.getToken().getId(),
                    UUID.fromString(token.getToken().getSubject()),
                    version == null ? -1 : version.longValue());
        } catch (RuntimeException exception) {
            validation = RevocationStateService.Validation.UNAVAILABLE;
        }
        if (validation == RevocationStateService.Validation.VALID) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean unavailable = validation == RevocationStateService.Validation.UNAVAILABLE;
        writer.write(
                request,
                response,
                unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED,
                unavailable ? ErrorCode.SECURITY_STATE_UNAVAILABLE : ErrorCode.TOKEN_REVOKED,
                unavailable
                        ? "Security state is temporarily unavailable."
                        : "The access token has been revoked or superseded.");
    }
}
