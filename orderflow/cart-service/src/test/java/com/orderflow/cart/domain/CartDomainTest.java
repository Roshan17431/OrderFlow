package com.orderflow.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.cart.persistence.StoredCartItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartDomainTest {

    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void calculatesCountsAndSubtotalUsingBigDecimal() {
        Cart cart = new Cart(
                USER_ID,
                2,
                "USD",
                List.of(
                        item(UUID.randomUUID(), 2, "10.25"),
                        item(UUID.randomUUID(), 3, "4.50")),
                NOW,
                NOW,
                NOW.plusSeconds(60));

        assertThat(cart.distinctItemCount()).isEqualTo(2);
        assertThat(cart.totalQuantity()).isEqualTo(5);
        assertThat(cart.subtotal()).isEqualByComparingTo("34.00");
    }

    @Test
    void emptyCartHasStableZeroValues() {
        Cart cart = Cart.empty(USER_ID);

        assertThat(cart.userId()).isEqualTo(USER_ID);
        assertThat(cart.version()).isZero();
        assertThat(cart.items()).isEmpty();
        assertThat(cart.subtotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void storedSnapshotRoundTripsWithoutFloatingPointConversion() {
        CartItem original = item(UUID.randomUUID(), 7, "123456.78");

        StoredCartItem stored = StoredCartItem.from(original);
        CartItem restored = stored.toDomain();

        assertThat(stored.unitPriceSnapshot()).isEqualTo("123456.78");
        assertThat(restored).isEqualTo(original);
        assertThat(restored.lineTotal()).isEqualByComparingTo("864197.46");
    }

    private CartItem item(UUID productId, int quantity, String price) {
        return new CartItem(
                productId,
                "Product " + productId,
                quantity,
                new BigDecimal(price),
                "USD",
                3L,
                true,
                false,
                NOW,
                NOW);
    }
}
