package com.orderflow.auth.security.oauth2;

import com.orderflow.auth.config.RefreshTokenProperties;
import com.orderflow.auth.error.SecurityStateUnavailableException;
import com.orderflow.auth.security.TokenHasher;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OAuthExchangeCodeStore {

    private static final String PREFIX = "security:oauth-code:";
    private final StringRedisTemplate redis;
    private final RefreshTokenProperties properties;
    private final TokenHasher hasher;
    private final SecureRandom random = new SecureRandom();

    public OAuthExchangeCodeStore(
            StringRedisTemplate redis,
            RefreshTokenProperties properties,
            TokenHasher hasher) {
        this.redis = redis;
        this.properties = properties;
        this.hasher = hasher;
    }

    public String issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            redis.opsForValue().set(
                    PREFIX + hasher.sha256(raw),
                    userId.toString(),
                    properties.oauthExchangeTtl());
            return raw;
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public Optional<UUID> consume(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        try {
            String userId = redis.opsForValue().getAndDelete(PREFIX + hasher.sha256(rawCode));
            return userId == null ? Optional.empty() : Optional.of(UUID.fromString(userId));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }
}
