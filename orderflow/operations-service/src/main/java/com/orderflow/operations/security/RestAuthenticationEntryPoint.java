package com.orderflow.operations.security;

import com.orderflow.platform.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ApiSecurityErrorWriter writer;
    public RestAuthenticationEntryPoint(ApiSecurityErrorWriter writer) { this.writer = writer; }
    @Override public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        boolean bearer = request.getHeader("Authorization") != null;
        writer.write(request, response, HttpStatus.UNAUTHORIZED,
                bearer ? ErrorCode.INVALID_TOKEN : ErrorCode.AUTHENTICATION_REQUIRED,
                bearer ? "The access token is invalid." : "Authentication is required.");
    }
}
