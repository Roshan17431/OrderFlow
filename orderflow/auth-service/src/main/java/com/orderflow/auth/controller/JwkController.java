package com.orderflow.auth.controller;

import com.orderflow.auth.config.JwtProperties;
import com.orderflow.auth.security.jwt.RsaKeyProvider;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/.well-known")
public class JwkController {

    private final RsaKeyProvider keys;
    private final JwtProperties properties;

    public JwkController(RsaKeyProvider keys, JwtProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    @GetMapping("/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "kid", properties.keyId(),
                "alg", "RS256",
                "n", unsignedBase64(keys.publicKey().getModulus()),
                "e", unsignedBase64(keys.publicKey().getPublicExponent()))));
    }

    private String unsignedBase64(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
