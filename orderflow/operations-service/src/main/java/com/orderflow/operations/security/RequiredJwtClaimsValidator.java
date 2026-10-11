package com.orderflow.operations.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RequiredJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error ERROR = new OAuth2Error("invalid_token", "Required access-token claims are missing", null);
    @Override public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean valid = jwt.getSubject() != null && jwt.getId() != null && jwt.getIssuedAt() != null
                && jwt.getExpiresAt() != null && jwt.hasClaim("token_version") && roles != null && !roles.isEmpty();
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(ERROR);
    }
}
