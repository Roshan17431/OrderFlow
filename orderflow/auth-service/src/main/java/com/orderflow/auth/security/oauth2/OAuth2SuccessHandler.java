package com.orderflow.auth.security.oauth2;

import com.orderflow.auth.config.OAuth2Properties;
import com.orderflow.auth.domain.oauth.OAuthProvider;
import com.orderflow.auth.domain.user.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@Profile("oauth2")
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthAccountService accounts;
    private final OAuth2Properties properties;

    public OAuth2SuccessHandler(OAuthAccountService accounts, OAuth2Properties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = token.getPrincipal();
        OAuthProvider provider = OAuthProvider.valueOf(
                token.getAuthorizedClientRegistrationId().toUpperCase());
        String subjectAttribute = provider == OAuthProvider.GOOGLE ? "sub" : "id";
        Object verifiedAttribute = principal.getAttribute("email_verified");
        boolean verified = Boolean.TRUE.equals(verifiedAttribute)
                || "true".equalsIgnoreCase(String.valueOf(verifiedAttribute));
        User user = accounts.resolve(new OAuthIdentity(
                provider,
                String.valueOf((Object) principal.getAttribute(subjectAttribute)),
                principal.getAttribute("email"),
                verified,
                displayName(principal)));
        String code = accounts.issueExchangeCode(user.getId());
        String delimiter = properties.successRedirectUri().contains("?") ? "&" : "?";
        response.sendRedirect(
                properties.successRedirectUri()
                        + delimiter
                        + "code="
                        + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    private String displayName(OAuth2User principal) {
        String name = principal.getAttribute("name");
        return name == null ? principal.getAttribute("login") : name;
    }
}
