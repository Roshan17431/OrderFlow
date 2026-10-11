package com.orderflow.payment.service;

import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.platform.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyService {
    private static final String VALID = "[A-Za-z0-9._:-]{1,128}";

    public String require(String key) {
        if (key == null || !key.matches(VALID)) {
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.PAYMENT_IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key must contain 1 to 128 safe characters.");
        }
        return key;
    }

    public String hash(String normalizedInput) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalizedInput.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
