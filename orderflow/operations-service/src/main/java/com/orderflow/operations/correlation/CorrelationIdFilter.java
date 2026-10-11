package com.orderflow.operations.correlation;

import com.orderflow.platform.http.CorrelationHeaders;
import com.orderflow.platform.http.CorrelationIdValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "operationsCorrelationId";
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String id = CorrelationIdValidator.validateOrGenerate(request.getHeader(CorrelationHeaders.CORRELATION_ID));
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(CorrelationHeaders.CORRELATION_ID, id);
        MDC.put("correlationId", id);
        try { chain.doFilter(request, response); } finally { MDC.remove("correlationId"); }
    }
}
