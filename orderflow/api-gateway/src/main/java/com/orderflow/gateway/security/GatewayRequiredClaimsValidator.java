package com.orderflow.gateway.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class GatewayRequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token",
            "Required access-token claims are missing",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean valid = hasText(jwt.getSubject())
                && hasText(jwt.getId())
                && jwt.getIssuedAt() != null
                && jwt.getExpiresAt() != null
                && hasText(jwt.getClaimAsString("email"))
                && hasText(jwt.getClaimAsString("session_id"))
                && jwt.hasClaim("token_version")
                && roles != null
                && !roles.isEmpty();
        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERROR);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
