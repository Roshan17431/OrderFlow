package com.orderflow.product.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class CatalogCacheAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogCacheAccess.class);
    private final CacheManager caches;
    private final Counter hits;
    private final Counter misses;

    public CatalogCacheAccess(CacheManager caches, MeterRegistry registry) {
        this.caches = caches;
        this.hits = registry.counter("orderflow.catalog.cache.hits");
        this.misses = registry.counter("orderflow.catalog.cache.misses");
    }

    public <T> T get(String cacheName, String key, Class<T> type, Supplier<T> loader) {
        Cache cache = caches.getCache(cacheName);
        if (cache == null) {
            return loader.get();
        }
        try {
            T value = cache.get(key, type);
            if (value != null) {
                hits.increment();
                return value;
            }
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("cache", cacheName)
                    .setCause(exception)
                    .log("Catalog cache read failed");
        }
        misses.increment();
        T loaded = loader.get();
        try {
            cache.put(key, loaded);
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("cache", cacheName)
                    .setCause(exception)
                    .log("Catalog cache write failed");
        }
        return loaded;
    }

    public void evict(String cacheName, String key) {
        Cache cache = caches.getCache(cacheName);
        if (cache == null) {
            return;
        }
        try {
            cache.evict(key);
        } catch (RuntimeException exception) {
            LOGGER.atWarn()
                    .addKeyValue("cache", cacheName)
                    .setCause(exception)
                    .log("Catalog cache eviction failed");
        }
    }
}
