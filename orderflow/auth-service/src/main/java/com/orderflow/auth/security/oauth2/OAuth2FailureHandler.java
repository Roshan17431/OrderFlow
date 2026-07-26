package com.orderflow.auth.security.oauth2;

import com.orderflow.auth.config.OAuth2Properties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@Profile("oauth2")
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final OAuth2Properties properties;

    public OAuth2FailureHandler(OAuth2Properties properties) {
        this.properties = properties;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        response.sendRedirect(properties.failureRedirectUri());
    }
}
