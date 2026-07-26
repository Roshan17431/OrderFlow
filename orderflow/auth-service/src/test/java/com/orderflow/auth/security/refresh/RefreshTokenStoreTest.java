package com.orderflow.auth.security.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.config.RefreshTokenProperties;
import com.orderflow.auth.security.TokenHasher;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RefreshTokenStoreTest {

    @Test
    @SuppressWarnings("unchecked")
    void generatesAtLeast256BitsAndPersistsOnlyTheHash() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.opsForSet()).thenReturn(sets);
        RefreshTokenStore store = new RefreshTokenStore(
                redis,
                new RefreshTokenProperties(Duration.ofDays(14), 32, Duration.ofSeconds(60)),
                new TokenHasher());

        IssuedRefreshToken token =
                store.create(UUID.randomUUID(), 0, "a".repeat(64), "b".repeat(64));

        assertThat(token.value()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(token.metadata().tokenHash()).hasSize(64).isNotEqualTo(token.value());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(hashes).putAll(key.capture(), anyMap());
        assertThat(key.getValue())
                .startsWith("security:refresh:")
                .doesNotContain(token.value());
        verify(redis, never()).opsForValue();
    }
}
