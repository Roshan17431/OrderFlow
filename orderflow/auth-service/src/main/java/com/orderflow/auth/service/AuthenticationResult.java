package com.orderflow.auth.service;

import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.security.jwt.IssuedAccessToken;
import com.orderflow.auth.security.refresh.IssuedRefreshToken;

public record AuthenticationResult(
        User user,
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken) {
}
