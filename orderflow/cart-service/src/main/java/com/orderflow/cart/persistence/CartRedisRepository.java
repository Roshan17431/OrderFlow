package com.orderflow.cart.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.cart.config.CartProperties;
import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.error.CartDomainException;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class CartRedisRepository {

    private static final String ITEM_PREFIX = "item:";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CartProperties properties;
    private final DefaultRedisScript<Long> addScript;
    private final DefaultRedisScript<Long> updateScript;
    private final DefaultRedisScript<Long> removeScript;
    private final DefaultRedisScript<Long> replaceScript;
    private final Clock clock;
    private final Counter mutations;

    public CartRedisRepository(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CartProperties properties,
            @Qualifier("addCartItemScript") DefaultRedisScript<Long> addScript,
            @Qualifier("updateCartItemScript") DefaultRedisScript<Long> updateScript,
            @Qualifier("removeCartItemScript") DefaultRedisScript<Long> removeScript,
            @Qualifier("replaceCartItemsScript") DefaultRedisScript<Long> replaceScript,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.addScript = addScript;
        this.updateScript = updateScript;
        this.removeScript = removeScript;
        this.replaceScript = replaceScript;
        this.clock = clock;
        this.mutations = meterRegistry.counter("orderflow.cart.mutations");
    }

    public Cart find(UUID userId) {
        try {
            Map<Object, Object> values = redis.opsForHash().entries(key(userId));
            if (values.isEmpty()) {
                return Cart.empty(userId);
            }
            List<CartItem> items = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : values.entrySet()) {
                if (entry.getKey().toString().startsWith(ITEM_PREFIX)) {
                    items.add(objectMapper
                            .readValue(entry.getValue().toString(), StoredCartItem.class)
                            .toDomain());
                }
            }
            return new Cart(
                    userId,
                    longValue(values, "__version"),
                    stringValue(values, "__currency"),
                    items,
                    instantValue(values, "__createdAt"),
                    instantValue(values, "__updatedAt"),
                    instantValue(values, "__expiresAt"));
        } catch (DataAccessException | JsonProcessingException exception) {
            throw unavailable(exception);
        }
    }

    public Cart add(UUID userId, CartItem item) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(properties.ttl());
        List<String> args = List.of(
                itemField(item.productId()),
                item.currency(),
                serialize(item),
                Integer.toString(properties.maxQuantityPerItem()),
                Integer.toString(properties.maxDistinctItems()),
                now.toString(),
                expires.toString(),
                Long.toString(properties.ttl().toSeconds()));
        long version = execute(addScript, userId, args);
        return withVersion(find(userId), version);
    }

    public Cart update(UUID userId, CartItem item) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(properties.ttl());
        List<String> args = List.of(
                itemField(item.productId()),
                item.currency(),
                serialize(item),
                Integer.toString(properties.maxQuantityPerItem()),
                now.toString(),
                expires.toString(),
                Long.toString(properties.ttl().toSeconds()));
        long version = execute(updateScript, userId, args);
        return withVersion(find(userId), version);
    }

    public Cart remove(UUID userId, UUID productId) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(properties.ttl());
        List<String> args = List.of(
                itemField(productId),
                now.toString(),
                expires.toString(),
                Long.toString(properties.ttl().toSeconds()));
        long version = execute(removeScript, userId, args);
        Cart current = find(userId);
        return version == 0 ? current : withVersion(current, version);
    }

    public Cart replace(UUID userId, List<CartItem> items) {
        Instant now = Instant.now(clock);
        Instant expires = now.plus(properties.ttl());
        List<String> args = new ArrayList<>();
        args.add(Integer.toString(items.size()));
        for (CartItem item : items) {
            args.add(itemField(item.productId()));
            args.add(serialize(item));
        }
        args.add(now.toString());
        args.add(expires.toString());
        args.add(Long.toString(properties.ttl().toSeconds()));
        long version = execute(replaceScript, userId, args);
        Cart current = find(userId);
        return version == 0 ? current : withVersion(current, version);
    }

    public void clear(UUID userId) {
        try {
            redis.delete(key(userId));
            mutations.increment();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public String key(UUID userId) {
        return properties.keyPrefix() + userId;
    }

    private long execute(
            DefaultRedisScript<Long> script, UUID userId, List<String> args) {
        try {
            Long result = redis.execute(script, List.of(key(userId)), args.toArray());
            if (result == null) {
                throw unavailable(new IllegalStateException("Cart script returned no result"));
            }
            CartMutationCode.from(result).throwIfFailure();
            mutations.increment();
            return result;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private String serialize(CartItem item) {
        try {
            return objectMapper.writeValueAsString(StoredCartItem.from(item));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize a cart item", exception);
        }
    }

    private String itemField(UUID productId) {
        return ITEM_PREFIX + productId;
    }

    private Cart withVersion(Cart cart, long version) {
        return new Cart(
                cart.userId(),
                version,
                cart.currency(),
                cart.items(),
                cart.createdAt(),
                cart.updatedAt(),
                cart.expiresAt());
    }

    private String stringValue(Map<Object, Object> values, String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    private long longValue(Map<Object, Object> values, String field) {
        String value = stringValue(values, field);
        return value == null ? 0 : Long.parseLong(value);
    }

    private Instant instantValue(Map<Object, Object> values, String field) {
        String value = stringValue(values, field);
        return value == null ? null : Instant.parse(value);
    }

    private CartDomainException unavailable(Throwable cause) {
        return new CartDomainException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.CART_STATE_UNAVAILABLE,
                "Cart state is temporarily unavailable.",
                cause);
    }
}
