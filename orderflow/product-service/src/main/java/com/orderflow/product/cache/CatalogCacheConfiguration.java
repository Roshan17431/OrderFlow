package com.orderflow.product.cache;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.product.config.CatalogCacheProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CatalogCacheConfiguration implements CachingConfigurer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CatalogCacheConfiguration.class);

    @Bean
    CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            CatalogCacheProperties properties) {
        var valueSerializer = valueSerializer(objectMapper);
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(name -> properties.namespace() + ":cache:" + name + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        valueSerializer));
        Map<String, RedisCacheConfiguration> configurations = Map.of(
                CatalogCaches.PRODUCT_DETAIL,
                defaults.entryTtl(properties.productTtl()),
                CatalogCaches.PRODUCT_SLUG,
                defaults.entryTtl(properties.productTtl()),
                CatalogCaches.CATEGORY_LIST,
                defaults.entryTtl(properties.categoryTtl()),
                CatalogCaches.PRODUCT_SEARCH,
                defaults.entryTtl(properties.searchTtl()));
        return RedisCacheManager.builder(
                        org.springframework.data.redis.cache.RedisCacheWriter.create(
                                connectionFactory, writer -> writer.immediateWrites()))
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configurations)
                .enableStatistics()
                .build();
    }

    // Retain the v1 typed cache format while restricting deserialization to the
    // actual catalog DTOs and their collection/decimal values.
    static org.springframework.data.redis.serializer.RedisSerializer<Object> valueSerializer(
            ObjectMapper objectMapper) {
        var validator = tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.orderflow.product.dto.")
                .allowIfSubType(java.math.BigDecimal.class)
                .allowIfSubType(java.util.ArrayList.class)
                .allowIfSubType(java.util.regex.Pattern.compile(
                        "java\\.util\\.ImmutableCollections\\$List(?:12|N)"))
                .build();
        return GenericJacksonJsonRedisSerializer.builder(objectMapper::rebuild)
                .enableDefaultTyping(validator)
                .typePropertyName("@class")
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log(cache, "read", exception);
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception, Cache cache, Object key, Object value) {
                log(cache, "write", exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log(cache, "eviction", exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log(cache, "clear", exception);
            }

            private void log(Cache cache, String operation, RuntimeException exception) {
                LOGGER.atWarn()
                        .addKeyValue("cache", cache.getName())
                        .addKeyValue("operation", operation)
                        .setCause(exception)
                        .log("Catalog cache operation failed; database behavior remains available");
            }
        };
    }
}
