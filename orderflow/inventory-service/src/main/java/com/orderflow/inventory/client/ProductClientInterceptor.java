package com.orderflow.inventory.client;

import com.orderflow.inventory.correlation.InventoryRequestAttributes;
import com.orderflow.platform.http.CorrelationHeaders;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class ProductClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && authentication.isAuthenticated()) {
            request.getHeaders().setBearerAuth(token.getToken().getTokenValue());
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object correlationId = attributes.getAttribute(
                    InventoryRequestAttributes.CORRELATION_ID,
                    RequestAttributes.SCOPE_REQUEST);
            if (correlationId instanceof String value) {
                request.getHeaders().set(CorrelationHeaders.CORRELATION_ID, value);
            }
        }
        request.getHeaders().remove(HttpHeaders.COOKIE);
        return execution.execute(request, body);
    }
}
