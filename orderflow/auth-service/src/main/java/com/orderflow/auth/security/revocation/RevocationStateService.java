package com.orderflow.auth.security.revocation;

import com.orderflow.auth.error.SecurityStateUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RevocationStateService {

    public static final String REVOKED_JTI_PREFIX = "security:revoked:jti:";
    public static final String TOKEN_VERSION_PREFIX = "security:user:token-version:";
    private final StringRedisTemplate redis;

    public RevocationStateService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void publishTokenVersion(UUID userId, long version) {
        try {
            redis.opsForValue().set(TOKEN_VERSION_PREFIX + userId, Long.toString(version));
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public void revokeJti(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(REVOKED_JTI_PREFIX + jti, "true", remaining);
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public Validation validate(String jti, UUID userId, long tokenVersion) {
        try {
            Boolean revoked = redis.hasKey(REVOKED_JTI_PREFIX + jti);
            String latest = redis.opsForValue().get(TOKEN_VERSION_PREFIX + userId);
            if (latest == null) {
                return Validation.UNAVAILABLE;
            }
            if (Boolean.TRUE.equals(revoked)) {
                return Validation.REVOKED;
            }
            return Long.parseLong(latest) == tokenVersion
                    ? Validation.VALID
                    : Validation.STALE;
        } catch (DataAccessException | NumberFormatException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public enum Validation {
        VALID,
        REVOKED,
        STALE,
        UNAVAILABLE
    }
}
