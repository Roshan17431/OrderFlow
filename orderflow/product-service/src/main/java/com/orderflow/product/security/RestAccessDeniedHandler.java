package com.orderflow.product.security;

import com.orderflow.platform.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiSecurityErrorWriter writer;

    public RestAccessDeniedHandler(ApiSecurityErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writer.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.INSUFFICIENT_PERMISSIONS,
                "You do not have permission to perform this action.");
    }
}
