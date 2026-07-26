package com.orderflow.auth.security.rate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.config.LoginAttemptProperties;
import com.orderflow.auth.error.LoginThrottledException;
import com.orderflow.auth.security.TokenHasher;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class LoginAttemptServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private LoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new LoginAttemptService(
                redis,
                new LoginAttemptProperties(5, Duration.ofMinutes(15)),
                new TokenHasher());
    }

    @Test
    void thresholdReturnsRetryAfterAndUsesAnAtomicIncrementScript() {
        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(),
                anyString())).thenReturn(5L);
        when(redis.getExpire(anyString())).thenReturn(600L);

        assertThatThrownBy(() -> service.recordFailure(
                "person@example.com",
                "a".repeat(64)))
                .isInstanceOf(LoginThrottledException.class)
                .extracting("retryAfterSeconds")
                .isEqualTo(600L);
    }

    @Test
    void successfulLoginClearsTemporaryFailureState() {
        service.clear("person@example.com", "a".repeat(64));
        verify(redis).delete(anyString());
    }
}
