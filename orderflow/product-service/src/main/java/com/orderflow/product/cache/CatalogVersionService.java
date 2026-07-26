package com.orderflow.product.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogVersionService {

    public static final String VERSION_KEY = "catalog:version";
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogVersionService.class);
    private final StringRedisTemplate redis;
    private final Counter invalidations;

    public CatalogVersionService(StringRedisTemplate redis, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.invalidations = meterRegistry.counter("orderflow.catalog.cache.invalidations");
    }

    public long current() {
        try {
            String value = redis.opsForValue().get(VERSION_KEY);
            return value == null ? 0 : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException exception) {
            LOGGER.atWarn()
                    .setCause(exception)
                    .log("Catalog version unavailable; continuing with database-backed reads");
            return 0;
        }
    }

    public void increment() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
            invalidations.increment();
        } catch (DataAccessException exception) {
            LOGGER.atWarn()
                    .setCause(exception)
                    .log("Catalog version increment failed after a database mutation");
        }
    }
}
