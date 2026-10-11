package com.orderflow.operations.security;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RevocationStateService {
    private final StringRedisTemplate redis;
    public RevocationStateService(StringRedisTemplate redis) { this.redis = redis; }
    public Validation validate(String jti, UUID user, long version) {
        try {
            Boolean revoked = redis.hasKey("security:revoked:jti:" + jti);
            String latest = redis.opsForValue().get("security:user:token-version:" + user);
            if (latest == null) return Validation.UNAVAILABLE;
            if (Boolean.TRUE.equals(revoked)) return Validation.REVOKED;
            return Long.parseLong(latest) == version ? Validation.VALID : Validation.STALE;
        } catch (DataAccessException | NumberFormatException exception) { return Validation.UNAVAILABLE; }
    }
    public enum Validation { VALID, REVOKED, STALE, UNAVAILABLE }
}
