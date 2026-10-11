package com.orderflow.payment.security;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RevocationStateService {
    private static final String JTI_PREFIX = "security:revoked:jti:";
    private static final String VERSION_PREFIX = "security:user:token-version:";
    private final StringRedisTemplate redis;
    public RevocationStateService(StringRedisTemplate redis) { this.redis = redis; }

    public Validation validate(String jti, UUID userId, long tokenVersion) {
        try {
            Boolean revoked = redis.hasKey(JTI_PREFIX + jti);
            String latest = redis.opsForValue().get(VERSION_PREFIX + userId);
            if (latest == null) return Validation.UNAVAILABLE;
            if (Boolean.TRUE.equals(revoked)) return Validation.REVOKED;
            return Long.parseLong(latest) == tokenVersion
                    ? Validation.VALID : Validation.STALE;
        } catch (DataAccessException | NumberFormatException exception) {
            return Validation.UNAVAILABLE;
        }
    }
    public enum Validation { VALID, REVOKED, STALE, UNAVAILABLE }
}
