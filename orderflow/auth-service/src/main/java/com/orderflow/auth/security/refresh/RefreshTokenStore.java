package com.orderflow.auth.security.refresh;

import com.orderflow.auth.config.RefreshTokenProperties;
import com.orderflow.auth.error.SecurityStateUnavailableException;
import com.orderflow.auth.security.TokenHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class RefreshTokenStore {

    private static final String TOKEN_PREFIX = "security:refresh:";
    private static final String FAMILY_PREFIX = "security:refresh-family:";
    private static final String USER_PREFIX = "security:refresh-user:";
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'rotated') == 'true' then
              local familyMembers = redis.call('SMEMBERS', KEYS[3])
              for _, tokenKey in ipairs(familyMembers) do
                if redis.call('EXISTS', tokenKey) == 1 then
                  redis.call('HSET', tokenKey, 'revoked', 'true')
                end
              end
              return 2
            end
            if redis.call('HGET', KEYS[1], 'revoked') == 'true' then return 0 end
            redis.call('HSET', KEYS[1],
              'rotated', 'true', 'revoked', 'true', 'replacedBy', ARGV[1])
            redis.call('HSET', KEYS[2],
              'sessionId', ARGV[2], 'familyId', ARGV[3], 'userId', ARGV[4],
              'tokenHash', ARGV[1], 'issuedAt', ARGV[5], 'expiresAt', ARGV[6],
              'rotated', 'false', 'revoked', 'false', 'replacedBy', '',
              'userAgentHash', ARGV[7], 'ipHash', ARGV[8], 'tokenVersion', ARGV[9])
            redis.call('EXPIRE', KEYS[2], ARGV[10])
            redis.call('SADD', KEYS[3], KEYS[2])
            redis.call('SADD', KEYS[4], KEYS[2])
            redis.call('EXPIRE', KEYS[3], ARGV[10])
            redis.call('EXPIRE', KEYS[4], ARGV[10])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE_SET_SCRIPT =
            new DefaultRedisScript<>("""
                    local members = redis.call('SMEMBERS', KEYS[1])
                    local count = 0
                    for _, tokenKey in ipairs(members) do
                      if redis.call('EXISTS', tokenKey) == 1 then
                        redis.call('HSET', tokenKey, 'revoked', 'true')
                        count = count + 1
                      end
                    end
                    return count
                    """, Long.class);

    private final StringRedisTemplate redis;
    private final RefreshTokenProperties properties;
    private final TokenHasher hasher;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public RefreshTokenStore(
            StringRedisTemplate redis,
            RefreshTokenProperties properties,
            TokenHasher hasher) {
        this(redis, properties, hasher, new SecureRandom(), Clock.systemUTC());
    }

    RefreshTokenStore(
            StringRedisTemplate redis,
            RefreshTokenProperties properties,
            TokenHasher hasher,
            SecureRandom secureRandom,
            Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.hasher = hasher;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public IssuedRefreshToken create(
            UUID userId,
            long tokenVersion,
            String userAgentHash,
            String ipHash) {
        return create(userId, tokenVersion, UUID.randomUUID(), UUID.randomUUID(),
                userAgentHash, ipHash, true);
    }

    public Optional<RefreshTokenMetadata> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<Object, Object> values = redis.opsForHash()
                    .entries(tokenKey(hasher.sha256(rawToken)));
            return values.isEmpty() ? Optional.empty() : Optional.of(toMetadata(values));
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public RefreshRotationResult rotate(
            String rawToken,
            String userAgentHash,
            String ipHash) {
        Optional<RefreshTokenMetadata> existing = find(rawToken);
        if (existing.isEmpty()) {
            return new RefreshRotationResult(RefreshRotationResult.Status.INVALID, null);
        }
        RefreshTokenMetadata current = existing.get();
        IssuedRefreshToken replacement = create(
                current.userId(),
                current.tokenVersion(),
                current.familyId(),
                current.sessionId(),
                userAgentHash,
                ipHash,
                false);
        RefreshTokenMetadata next = replacement.metadata();
        List<String> keys = List.of(
                tokenKey(current.tokenHash()),
                tokenKey(next.tokenHash()),
                familyKey(current.familyId()),
                userKey(current.userId()));
        List<String> arguments = List.of(
                next.tokenHash(),
                next.sessionId().toString(),
                next.familyId().toString(),
                next.userId().toString(),
                next.issuedAt().toString(),
                next.expiresAt().toString(),
                next.userAgentHash(),
                next.ipHash(),
                Long.toString(next.tokenVersion()),
                Long.toString(properties.tokenTtl().toSeconds()));
        try {
            Long result = redis.execute(ROTATE_SCRIPT, keys, arguments.toArray());
            if (Long.valueOf(1).equals(result)) {
                return new RefreshRotationResult(
                        RefreshRotationResult.Status.ROTATED,
                        replacement);
            }
            if (Long.valueOf(2).equals(result)) {
                return new RefreshRotationResult(
                        RefreshRotationResult.Status.REUSE_DETECTED,
                        null);
            }
            return new RefreshRotationResult(RefreshRotationResult.Status.INVALID, null);
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Optional<RefreshTokenMetadata> metadata = find(rawToken);
        if (metadata.isEmpty()) {
            return;
        }
        try {
            redis.opsForHash().put(tokenKey(metadata.get().tokenHash()), "revoked", "true");
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public void revokeFamily(UUID familyId) {
        executeRevokeSet(familyKey(familyId));
    }

    public void revokeAllForUser(UUID userId) {
        executeRevokeSet(userKey(userId));
    }

    private IssuedRefreshToken create(
            UUID userId,
            long tokenVersion,
            UUID familyId,
            UUID sessionId,
            String userAgentHash,
            String ipHash,
            boolean persist) {
        byte[] random = new byte[properties.tokenBytes()];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hasher.sha256(rawToken);
        Instant issuedAt = clock.instant();
        RefreshTokenMetadata metadata = new RefreshTokenMetadata(
                sessionId,
                familyId,
                userId,
                hash,
                issuedAt,
                issuedAt.plus(properties.tokenTtl()),
                false,
                false,
                "",
                userAgentHash,
                ipHash,
                tokenVersion);
        if (persist) {
            persist(metadata);
        }
        return new IssuedRefreshToken(rawToken, metadata);
    }

    private void persist(RefreshTokenMetadata metadata) {
        try {
            String tokenKey = tokenKey(metadata.tokenHash());
            redis.opsForHash().putAll(tokenKey, toMap(metadata));
            redis.expire(tokenKey, properties.tokenTtl());
            redis.opsForSet().add(familyKey(metadata.familyId()), tokenKey);
            redis.expire(familyKey(metadata.familyId()), properties.tokenTtl());
            redis.opsForSet().add(userKey(metadata.userId()), tokenKey);
            redis.expire(userKey(metadata.userId()), properties.tokenTtl());
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    private void executeRevokeSet(String key) {
        try {
            redis.execute(REVOKE_SET_SCRIPT, List.of(key));
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    private Map<String, String> toMap(RefreshTokenMetadata metadata) {
        return Map.ofEntries(
                Map.entry("sessionId", metadata.sessionId().toString()),
                Map.entry("familyId", metadata.familyId().toString()),
                Map.entry("userId", metadata.userId().toString()),
                Map.entry("tokenHash", metadata.tokenHash()),
                Map.entry("issuedAt", metadata.issuedAt().toString()),
                Map.entry("expiresAt", metadata.expiresAt().toString()),
                Map.entry("rotated", Boolean.toString(metadata.rotated())),
                Map.entry("revoked", Boolean.toString(metadata.revoked())),
                Map.entry("replacedBy", metadata.replacedBy()),
                Map.entry("userAgentHash", metadata.userAgentHash()),
                Map.entry("ipHash", metadata.ipHash()),
                Map.entry("tokenVersion", Long.toString(metadata.tokenVersion())));
    }

    private RefreshTokenMetadata toMetadata(Map<Object, Object> values) {
        return new RefreshTokenMetadata(
                UUID.fromString(value(values, "sessionId")),
                UUID.fromString(value(values, "familyId")),
                UUID.fromString(value(values, "userId")),
                value(values, "tokenHash"),
                Instant.parse(value(values, "issuedAt")),
                Instant.parse(value(values, "expiresAt")),
                Boolean.parseBoolean(value(values, "rotated")),
                Boolean.parseBoolean(value(values, "revoked")),
                value(values, "replacedBy"),
                value(values, "userAgentHash"),
                value(values, "ipHash"),
                Long.parseLong(value(values, "tokenVersion")));
    }

    private String value(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String tokenKey(String hash) {
        return TOKEN_PREFIX + hash;
    }

    private String familyKey(UUID familyId) {
        return FAMILY_PREFIX + familyId;
    }

    private String userKey(UUID userId) {
        return USER_PREFIX + userId;
    }
}
