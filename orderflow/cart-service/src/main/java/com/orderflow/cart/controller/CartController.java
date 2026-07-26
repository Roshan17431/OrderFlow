package com.orderflow.cart.controller;

import com.orderflow.cart.dto.AddCartItemRequest;
import com.orderflow.cart.dto.CartResponse;
import com.orderflow.cart.dto.CartValidationResponse;
import com.orderflow.cart.dto.UpdateCartItemRequest;
import com.orderflow.cart.service.CartService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return carts.get(userId(jwt));
    }

    @PostMapping("/items")
    public CartResponse add(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddCartItemRequest request) {
        return carts.add(userId(jwt), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return carts.update(userId(jwt), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID productId) {
        return carts.remove(userId(jwt), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        carts.clear(userId(jwt));
    }

    @PostMapping("/validate")
    public CartValidationResponse validate(@AuthenticationPrincipal Jwt jwt) {
        return carts.validate(userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
