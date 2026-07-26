package com.orderflow.auth.security.oauth2;

import com.orderflow.auth.domain.oauth.OAuthProvider;

public record OAuthIdentity(
        OAuthProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName) {
}
