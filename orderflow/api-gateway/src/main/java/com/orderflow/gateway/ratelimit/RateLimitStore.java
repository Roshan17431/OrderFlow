package com.orderflow.gateway.ratelimit;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface RateLimitStore {
    Mono<Decision> consume(String key, int limit, Duration window);

    record Decision(boolean allowed, long remaining, Duration retryAfter) {
    }
}
