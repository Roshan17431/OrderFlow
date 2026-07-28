package com.orderflow.order.service;

import com.orderflow.order.error.OrderDomainException;
import com.orderflow.platform.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyService {

    private static final String SAFE_KEY = "[A-Za-z0-9._:-]{1,128}";

    public String validate(String key) {
        if (key == null || !key.matches(SAFE_KEY)) {
            throw new OrderDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED,
                    "A valid Idempotency-Key header is required.");
        }
        return key;
    }

    public String hash(String normalizedRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalizedRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
