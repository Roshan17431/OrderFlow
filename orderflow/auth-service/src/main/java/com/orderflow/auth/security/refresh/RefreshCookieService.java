package com.orderflow.auth.security.refresh;

import com.orderflow.auth.config.CookieProperties;
import com.orderflow.auth.config.RefreshTokenProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieService {

    private final CookieProperties cookies;
    private final RefreshTokenProperties refresh;

    public RefreshCookieService(CookieProperties cookies, RefreshTokenProperties refresh) {
        this.cookies = cookies;
        this.refresh = refresh;
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(cookies.refreshCookieName(), rawToken)
                .httpOnly(true)
                .secure(cookies.secure())
                .sameSite(cookies.sameSite())
                .path(cookies.path())
                .maxAge(refresh.tokenTtl())
                .build();
    }

    public ResponseCookie clear() {
        return ResponseCookie.from(cookies.refreshCookieName(), "")
                .httpOnly(true)
                .secure(cookies.secure())
                .sameSite(cookies.sameSite())
                .path(cookies.path())
                .maxAge(0)
                .build();
    }

    public String name() {
        return cookies.refreshCookieName();
    }
}
