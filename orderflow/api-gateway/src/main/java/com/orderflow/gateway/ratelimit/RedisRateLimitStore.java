package com.orderflow.gateway.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            return {current, ttl}
            """, List.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisRateLimitStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Decision> consume(String key, int limit, Duration window) {
        return redis.execute(SCRIPT, List.of(key), Long.toString(window.toMillis()))
                .single()
                .map(values -> {
                    long count = ((Number) values.get(0)).longValue();
                    long ttlMillis = Math.max(1, ((Number) values.get(1)).longValue());
                    long remaining = Math.max(0, limit - count);
                    return new Decision(count <= limit, remaining, Duration.ofMillis(ttlMillis));
                });
    }
}
