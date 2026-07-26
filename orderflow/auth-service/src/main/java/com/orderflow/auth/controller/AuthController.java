package com.orderflow.auth.controller;

import com.orderflow.auth.config.JwtProperties;
import com.orderflow.auth.correlation.CorrelationRequestAttributes;
import com.orderflow.auth.dto.request.LoginRequest;
import com.orderflow.auth.dto.request.RegisterRequest;
import com.orderflow.auth.dto.response.AuthenticationResponse;
import com.orderflow.auth.dto.response.CsrfResponse;
import com.orderflow.auth.dto.response.UserResponse;
import com.orderflow.auth.security.ClientFingerprint;
import com.orderflow.auth.security.refresh.RefreshCookieService;
import com.orderflow.auth.service.AuthenticationResult;
import com.orderflow.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authentication;
    private final RefreshCookieService cookies;
    private final ClientFingerprint fingerprints;
    private final JwtProperties jwtProperties;

    public AuthController(
            AuthenticationService authentication,
            RefreshCookieService cookies,
            ClientFingerprint fingerprints,
            JwtProperties jwtProperties) {
        this.authentication = authentication;
        this.cookies = cookies;
        this.fingerprints = fingerprints;
        this.jwtProperties = jwtProperties;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthenticationResult result = authentication.register(
                request,
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest),
                correlationId(servletRequest));
        setRefreshCookie(servletResponse, result);
        return ResponseEntity.status(HttpStatus.CREATED).body(response(result));
    }

    @PostMapping("/login")
    public AuthenticationResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthenticationResult result = authentication.login(
                request,
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest),
                correlationId(servletRequest));
        setRefreshCookie(servletResponse, result);
        return response(result);
    }

    @PostMapping("/refresh")
    public AuthenticationResponse refresh(
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        AuthenticationResult result = authentication.refresh(
                refreshToken(servletRequest),
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest),
                correlationId(servletRequest));
        setRefreshCookie(servletResponse, result);
        return response(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authentication.logout(
                jwt,
                refreshToken(servletRequest),
                correlationId(servletRequest),
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authentication.logoutAll(
                jwt,
                correlationId(servletRequest),
                fingerprints.ipHash(servletRequest),
                fingerprints.userAgentHash(servletRequest));
        servletResponse.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(authentication.currentUser(jwt.getSubject()));
    }

    private AuthenticationResponse response(AuthenticationResult result) {
        return new AuthenticationResponse(
                result.accessToken().value(),
                "Bearer",
                jwtProperties.accessTokenTtl().toSeconds(),
                UserResponse.from(result.user()));
    }

    private void setRefreshCookie(
            HttpServletResponse response,
            AuthenticationResult authenticationResult) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookies.create(authenticationResult.refreshToken().value()).toString());
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID);
    }

    private String refreshToken(HttpServletRequest request) {
        Cookie[] requestCookies = request.getCookies();
        if (requestCookies == null) {
            return null;
        }
        for (Cookie cookie : requestCookies) {
            if (cookies.name().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
