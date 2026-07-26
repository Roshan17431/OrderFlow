package com.orderflow.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientFingerprint {

    private final TokenHasher tokenHasher;

    public ClientFingerprint(TokenHasher tokenHasher) {
        this.tokenHasher = tokenHasher;
    }

    public String ipHash(HttpServletRequest request) {
        return tokenHasher.sha256(request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr());
    }

    public String userAgentHash(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return tokenHasher.sha256(userAgent == null ? "unknown" : userAgent);
    }
}
