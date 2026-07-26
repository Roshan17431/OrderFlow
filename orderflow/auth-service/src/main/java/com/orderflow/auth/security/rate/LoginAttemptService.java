package com.orderflow.auth.security.rate;

import com.orderflow.auth.config.LoginAttemptProperties;
import com.orderflow.auth.error.LoginThrottledException;
import com.orderflow.auth.error.SecurityStateUnavailableException;
import com.orderflow.auth.security.TokenHasher;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptService {

    private static final String PREFIX = "security:login-attempt:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local value = redis.call('INCR', KEYS[1])
                    if value == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
                    return value
                    """, Long.class);
    private final StringRedisTemplate redis;
    private final LoginAttemptProperties properties;
    private final TokenHasher hasher;

    public LoginAttemptService(
            StringRedisTemplate redis,
            LoginAttemptProperties properties,
            TokenHasher hasher) {
        this.redis = redis;
        this.properties = properties;
        this.hasher = hasher;
    }

    public void assertAllowed(String normalizedEmail, String ipHash) {
        String key = key(normalizedEmail, ipHash);
        try {
            String value = redis.opsForValue().get(key);
            if (value != null && Long.parseLong(value) >= properties.maxAttempts()) {
                throw new LoginThrottledException(ttl(key));
            }
        } catch (LoginThrottledException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public void recordFailure(String normalizedEmail, String ipHash) {
        String key = key(normalizedEmail, ipHash);
        try {
            Long attempts = redis.execute(
                    INCREMENT_SCRIPT,
                    List.of(key),
                    Long.toString(properties.window().toSeconds()));
            if (attempts != null && attempts >= properties.maxAttempts()) {
                throw new LoginThrottledException(ttl(key));
            }
        } catch (LoginThrottledException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    public void clear(String normalizedEmail, String ipHash) {
        try {
            redis.delete(key(normalizedEmail, ipHash));
        } catch (DataAccessException exception) {
            throw new SecurityStateUnavailableException(exception);
        }
    }

    private long ttl(String key) {
        Long ttl = redis.getExpire(key);
        return ttl == null || ttl < 1 ? properties.window().toSeconds() : ttl;
    }

    private String key(String normalizedEmail, String ipHash) {
        return PREFIX + hasher.sha256(normalizedEmail + "|" + ipHash);
    }
}
