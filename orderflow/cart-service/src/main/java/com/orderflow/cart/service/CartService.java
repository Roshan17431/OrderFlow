package com.orderflow.cart.service;

import com.orderflow.cart.client.ProductClient;
import com.orderflow.cart.config.CartProperties;
import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.dto.CartResponse;
import com.orderflow.cart.dto.CartValidationIssue;
import com.orderflow.cart.dto.CartValidationResponse;
import com.orderflow.cart.dto.ProductResolutionItem;
import com.orderflow.cart.dto.ProductResolutionResponse;
import com.orderflow.cart.error.CartDomainException;
import com.orderflow.cart.persistence.CartRedisRepository;
import com.orderflow.platform.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    private final CartRedisRepository carts;
    private final ProductClient products;
    private final CartMapper mapper;
    private final CartProperties properties;
    private final Clock clock;

    public CartService(
            CartRedisRepository carts,
            ProductClient products,
            CartMapper mapper,
            CartProperties properties,
            Clock clock) {
        this.carts = carts;
        this.products = products;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    public CartResponse get(UUID userId) {
        return mapper.response(carts.find(userId));
    }

    public CartResponse add(UUID userId, UUID productId, int quantity) {
        validateQuantity(quantity);
        ProductResolutionItem product = requirePurchasable(productId);
        Instant now = Instant.now(clock);
        CartItem item = new CartItem(
                productId,
                product.name(),
                quantity,
                product.priceAmount(),
                product.currency(),
                product.productVersion(),
                true,
                false,
                now,
                now);
        return mapper.response(carts.add(userId, item));
    }

    public CartResponse update(UUID userId, UUID productId, int quantity) {
        validateQuantity(quantity);
        ProductResolutionItem product = requirePurchasable(productId);
        Instant now = Instant.now(clock);
        CartItem item = new CartItem(
                productId,
                product.name(),
                quantity,
                product.priceAmount(),
                product.currency(),
                product.productVersion(),
                true,
                false,
                now,
                now);
        return mapper.response(carts.update(userId, item));
    }

    public CartResponse remove(UUID userId, UUID productId) {
        return mapper.response(carts.remove(userId, productId));
    }

    public void clear(UUID userId) {
        carts.clear(userId);
    }

    public CartValidationResponse validate(UUID userId) {
        Cart stored = carts.find(userId);
        if (stored.items().isEmpty()) {
            return new CartValidationResponse(mapper.response(stored), true, List.of());
        }
        List<UUID> productIds = stored.items().stream().map(CartItem::productId).toList();
        ProductResolutionResponse response = products.resolve(productIds);
        Map<UUID, ProductResolutionItem> resolved = response.items().stream()
                .collect(Collectors.toMap(ProductResolutionItem::productId, Function.identity()));
        ValidationAccumulator validation = new ValidationAccumulator();
        List<CartItem> refreshed = stored.items().stream()
                .map(item -> validateItem(item, resolved.get(item.productId()), validation))
                .toList();
        Cart updated = carts.replace(userId, refreshed);
        return new CartValidationResponse(
                mapper.response(updated),
                validation.issues.isEmpty(),
                validation.issues);
    }

    private CartItem validateItem(
            CartItem stored,
            ProductResolutionItem current,
            ValidationAccumulator validation) {
        Instant now = Instant.now(clock);
        if (current == null || !current.purchasable()) {
            validation.issues.add(new CartValidationIssue(
                    stored.productId(),
                    ErrorCode.PRODUCT_UNAVAILABLE.name(),
                    "The product is no longer available."));
            return new CartItem(
                    stored.productId(),
                    stored.productNameSnapshot(),
                    stored.quantity(),
                    stored.unitPriceSnapshot(),
                    stored.currency(),
                    stored.productVersionSnapshot(),
                    false,
                    false,
                    stored.addedAt(),
                    now);
        }
        boolean priceChanged =
                stored.unitPriceSnapshot().compareTo(current.priceAmount()) != 0
                        || !stored.currency().equals(current.currency());
        if (priceChanged) {
            validation.issues.add(new CartValidationIssue(
                    stored.productId(),
                    ErrorCode.PRICE_CHANGED.name(),
                    "The product price or currency has changed."));
        }
        return new CartItem(
                stored.productId(),
                current.name(),
                stored.quantity(),
                current.priceAmount(),
                current.currency(),
                current.productVersion(),
                true,
                priceChanged,
                stored.addedAt(),
                now);
    }

    private ProductResolutionItem requirePurchasable(UUID productId) {
        ProductResolutionResponse response = products.resolve(List.of(productId));
        ProductResolutionItem product = response.items().stream()
                .filter(item -> productId.equals(item.productId()))
                .findFirst()
                .orElse(null);
        if (product == null || !product.purchasable()) {
            throw new CartDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.PRODUCT_NOT_PURCHASABLE,
                    "The product is not currently purchasable.");
        }
        return product;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > properties.maxQuantityPerItem()) {
            throw new CartDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED,
                    "Quantity must be within the configured cart limit.");
        }
    }

    private static final class ValidationAccumulator {
        private final java.util.ArrayList<CartValidationIssue> issues =
                new java.util.ArrayList<>();
    }
}
