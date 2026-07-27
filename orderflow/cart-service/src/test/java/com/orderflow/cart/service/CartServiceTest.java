package com.orderflow.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.cart.client.ProductClient;
import com.orderflow.cart.config.CartProperties;
import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.dto.CartValidationResponse;
import com.orderflow.cart.dto.ProductResolutionItem;
import com.orderflow.cart.dto.ProductResolutionResponse;
import com.orderflow.cart.error.CartDomainException;
import com.orderflow.cart.persistence.CartRedisRepository;
import com.orderflow.platform.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID =
            UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CartRedisRepository repository;

    @Mock
    private ProductClient products;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(
                repository,
                products,
                new CartMapper(),
                new CartProperties(Duration.ofDays(30), 100, 5, "orderflow:cart:"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsQuantityOutsideConfiguredLimitBeforeCallingProductService() {
        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID, 6))
                .isInstanceOfSatisfying(
                        CartDomainException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED));
    }

    @Test
    void rejectsUnavailableProduct() {
        when(products.resolve(List.of(PRODUCT_ID)))
                .thenReturn(new ProductResolutionResponse(
                        List.of(resolution("10.00", "USD", false, 2L))));

        assertThatThrownBy(() -> service.add(USER_ID, PRODUCT_ID, 1))
                .isInstanceOfSatisfying(
                        CartDomainException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ErrorCode.PRODUCT_NOT_PURCHASABLE));
    }

    @Test
    void addConvertsResolutionToProductSnapshot() {
        when(products.resolve(List.of(PRODUCT_ID)))
                .thenReturn(new ProductResolutionResponse(
                        List.of(resolution("10.25", "USD", true, 2L))));
        when(repository.add(eq(USER_ID), any(CartItem.class)))
                .thenAnswer(invocation -> {
                    CartItem snapshot = invocation.getArgument(1, CartItem.class);
                    return new Cart(
                            USER_ID,
                            1,
                            "USD",
                            List.of(snapshot),
                            NOW,
                            NOW,
                            NOW.plus(Duration.ofDays(30)));
                });

        var response = service.add(USER_ID, PRODUCT_ID, 2);

        assertThat(response.subtotal()).isEqualByComparingTo("20.50");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productNameSnapshot()).isEqualTo("Keyboard");
            assertThat(item.productVersionSnapshot()).isEqualTo(2L);
            assertThat(item.unitPriceSnapshot()).isEqualByComparingTo("10.25");
        });
    }

    @Test
    void validationDetectsPriceChangeAndReplacesSnapshotsOnce() {
        Cart stored = cart(item("10.00", "USD", true, false));
        when(repository.find(USER_ID)).thenReturn(stored);
        when(products.resolve(List.of(PRODUCT_ID)))
                .thenReturn(new ProductResolutionResponse(
                        List.of(resolution("12.00", "USD", true, 3L))));
        when(repository.replace(eq(USER_ID), any())).thenAnswer(invocation -> new Cart(
                USER_ID,
                2,
                "USD",
                invocation.getArgument(1),
                NOW,
                NOW,
                NOW.plus(Duration.ofDays(30))));

        CartValidationResponse response = service.validate(USER_ID);

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).singleElement().satisfies(issue ->
                assertThat(issue.code()).isEqualTo(ErrorCode.PRICE_CHANGED.name()));
        assertThat(response.cart().items()).singleElement().satisfies(item -> {
            assertThat(item.priceChanged()).isTrue();
            assertThat(item.unitPriceSnapshot()).isEqualByComparingTo("12.00");
        });
        verify(products).resolve(List.of(PRODUCT_ID));
        verify(repository).replace(eq(USER_ID), any());
    }

    @Test
    void validationPreservesUnavailableItemInsteadOfDeletingIt() {
        when(repository.find(USER_ID)).thenReturn(cart(item("10.00", "USD", true, false)));
        when(products.resolve(List.of(PRODUCT_ID)))
                .thenReturn(new ProductResolutionResponse(List.of()));
        ArgumentCaptor<List<CartItem>> items = ArgumentCaptor.forClass(List.class);
        when(repository.replace(eq(USER_ID), items.capture())).thenAnswer(invocation -> new Cart(
                USER_ID,
                2,
                "USD",
                invocation.getArgument(1),
                NOW,
                NOW,
                NOW.plus(Duration.ofDays(30))));

        CartValidationResponse response = service.validate(USER_ID);

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).singleElement().satisfies(issue ->
                assertThat(issue.code()).isEqualTo(ErrorCode.PRODUCT_UNAVAILABLE.name()));
        assertThat(items.getValue()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(PRODUCT_ID);
            assertThat(item.purchasable()).isFalse();
        });
    }

    @Test
    void emptyValidationDoesNotCallProductService() {
        when(repository.find(USER_ID)).thenReturn(Cart.empty(USER_ID));

        CartValidationResponse response = service.validate(USER_ID);

        assertThat(response.valid()).isTrue();
        assertThat(response.cart().items()).isEmpty();
    }

    private ProductResolutionItem resolution(
            String price, String currency, boolean purchasable, Long version) {
        return new ProductResolutionItem(
                PRODUCT_ID,
                purchasable ? "Keyboard" : null,
                purchasable ? new BigDecimal(price) : null,
                purchasable ? currency : null,
                purchasable,
                purchasable ? version : null);
    }

    private Cart cart(CartItem item) {
        return new Cart(
                USER_ID,
                1,
                "USD",
                List.of(item),
                NOW,
                NOW,
                NOW.plus(Duration.ofDays(30)));
    }

    private CartItem item(
            String price, String currency, boolean purchasable, boolean priceChanged) {
        return new CartItem(
                PRODUCT_ID,
                "Keyboard",
                2,
                new BigDecimal(price),
                currency,
                2L,
                purchasable,
                priceChanged,
                NOW,
                NOW);
    }
}
