package com.orderflow.auth.security.jwt;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class Boot3JwtCompatibilityTest {
    @Test
    void acceptsSecurity6TokenAndPreservesExpiryAndIssuerValidation() throws Exception {
        String token;
        com.nimbusds.jose.jwk.RSAKey key;
        try (var input = getClass().getResourceAsStream("/compat/boot3/access-token.jwt")) {
            token = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var input = getClass().getResourceAsStream("/compat/boot3/public-jwk.json")) {
            key = com.nimbusds.jose.jwk.RSAKey.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(Clock.fixed(Instant.parse("2026-01-02T03:05:05Z"), ZoneOffset.UTC));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator("https://issuer.test"), new RequiredJwtClaimsValidator()));
        var jwt = decoder.decode(token);
        assertThat(jwt.getHeaders().get("kid")).isEqualTo("synthetic-boot3-compat");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsString("session_id")).isEqualTo("40000000-0000-0000-0000-000000000004");
        timestamp.setClock(Clock.fixed(Instant.parse("2026-01-02T03:20:05Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
        timestamp.setClock(Clock.fixed(Instant.parse("2026-01-02T03:05:05Z"), ZoneOffset.UTC));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator("https://other-issuer.test"), new RequiredJwtClaimsValidator()));
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }
}
