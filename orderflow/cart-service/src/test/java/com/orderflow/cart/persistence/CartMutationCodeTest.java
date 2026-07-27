package com.orderflow.cart.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.cart.error.CartDomainException;
import com.orderflow.platform.error.ErrorCode;
import org.junit.jupiter.api.Test;

class CartMutationCodeTest {

    @Test
    void nonNegativeScriptResultsAreSuccessfulVersions() {
        assertThat(CartMutationCode.from(0)).isEqualTo(CartMutationCode.SUCCESS);
        assertThat(CartMutationCode.from(42)).isEqualTo(CartMutationCode.SUCCESS);
    }

    @Test
    void mapsEveryLuaFailureToSafeDomainCode() {
        assertFailure(-1, ErrorCode.CART_CURRENCY_MISMATCH);
        assertFailure(-2, ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED);
        assertFailure(-3, ErrorCode.CART_ITEM_LIMIT_EXCEEDED);
        assertFailure(-4, ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void rejectsUnknownLuaResult() {
        assertThatThrownBy(() -> CartMutationCode.from(-99))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unknown cart mutation result");
    }

    private void assertFailure(long result, ErrorCode expected) {
        CartMutationCode code = CartMutationCode.from(result);
        assertThatThrownBy(code::throwIfFailure)
                .isInstanceOfSatisfying(
                        CartDomainException.class,
                        exception -> assertThat(exception.code()).isEqualTo(expected));
    }
}
