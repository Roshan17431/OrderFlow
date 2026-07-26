package com.orderflow.auth.controller;

import com.orderflow.auth.config.JwtProperties;
import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.dto.request.OAuthExchangeRequest;
import com.orderflow.auth.dto.response.AuthenticationResponse;
import com.orderflow.auth.dto.response.UserResponse;
import com.orderflow.auth.security.ClientFingerprint;
import com.orderflow.auth.security.oauth2.OAuthAccountService;
import com.orderflow.auth.security.refresh.RefreshCookieService;
import com.orderflow.auth.service.AuthenticationResult;
import com.orderflow.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuthExchangeController {

    private final OAuthAccountService oauthAccounts;
    private final AuthenticationService authentication;
    private final ClientFingerprint fingerprints;
    private final RefreshCookieService cookies;
    private final JwtProperties jwt;

    public OAuthExchangeController(
            OAuthAccountService oauthAccounts,
            AuthenticationService authentication,
            ClientFingerprint fingerprints,
            RefreshCookieService cookies,
            JwtProperties jwt) {
        this.oauthAccounts = oauthAccounts;
        this.authentication = authentication;
        this.fingerprints = fingerprints;
        this.cookies = cookies;
        this.jwt = jwt;
    }

    @PostMapping("/exchange")
    public AuthenticationResponse exchange(
            @Valid @RequestBody OAuthExchangeRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        User user = oauthAccounts.consumeExchangeCode(request.code());
        AuthenticationResult result = authentication.oauthLogin(
                user,
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest),
                correlationId(servletRequest),
                "exchange");
        servletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                cookies.create(result.refreshToken().value()).toString());
        return new AuthenticationResponse(
                result.accessToken().value(),
                "Bearer",
                jwt.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID);
    }
}
