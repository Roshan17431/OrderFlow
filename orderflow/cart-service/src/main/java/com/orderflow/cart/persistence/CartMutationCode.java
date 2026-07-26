package com.orderflow.cart.persistence;

import com.orderflow.cart.error.CartDomainException;
import com.orderflow.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CartMutationCode {
    SUCCESS,
    CURRENCY_MISMATCH,
    QUANTITY_LIMIT,
    ITEM_LIMIT,
    ITEM_NOT_FOUND;

    public static CartMutationCode from(long result) {
        if (result >= 0) {
            return SUCCESS;
        }
        return switch ((int) result) {
            case -1 -> CURRENCY_MISMATCH;
            case -2 -> QUANTITY_LIMIT;
            case -3 -> ITEM_LIMIT;
            case -4 -> ITEM_NOT_FOUND;
            default -> throw new IllegalStateException("Unknown cart mutation result");
        };
    }

    public void throwIfFailure() {
        switch (this) {
            case SUCCESS -> {
                return;
            }
            case CURRENCY_MISMATCH -> throw new CartDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CART_CURRENCY_MISMATCH,
                    "All products in a cart must use the same currency.");
            case QUANTITY_LIMIT -> throw new CartDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.CART_QUANTITY_LIMIT_EXCEEDED,
                    "The requested item quantity exceeds the cart limit.");
            case ITEM_LIMIT -> throw new CartDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CART_ITEM_LIMIT_EXCEEDED,
                    "The cart contains the maximum number of distinct products.");
            case ITEM_NOT_FOUND -> throw new CartDomainException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.CART_ITEM_NOT_FOUND,
                    "The cart item was not found.");
        }
    }
}
