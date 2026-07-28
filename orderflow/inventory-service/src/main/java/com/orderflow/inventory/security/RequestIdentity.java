package com.orderflow.inventory.security;

import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record RequestIdentity(UUID userId, boolean admin) {
    public static RequestIdentity from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new RequestIdentity(
                UUID.fromString(jwt.getSubject()),
                roles != null && roles.contains("ADMIN"));
    }
}
