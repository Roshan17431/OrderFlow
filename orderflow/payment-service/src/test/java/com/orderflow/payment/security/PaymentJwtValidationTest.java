package com.orderflow.payment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.orderflow.payment.config.PaymentSecurityProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class PaymentJwtValidationTest {
    private static HttpServer server;
    private static RSAKey signingKey;
    private static JwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("payment-test").generate();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] response = new JWKSet(signingKey.toPublicJWK())
                    .toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        decoder = new PaymentJwtConfiguration().paymentJwtDecoder(
                new PaymentSecurityProperties(
                        "https://issuer.test",
                        "http://localhost:" + server.getAddress().getPort() + "/jwks"));
    }

    @AfterAll
    static void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsValidRs256TokensAndMapsRoles() throws Exception {
        var jwt = decoder.decode(token(
                signingKey,
                "https://issuer.test",
                Instant.now().plusSeconds(300),
                true));
        assertThat(jwt.getSubject()).isEqualTo("user-id");
        assertThat(new PaymentJwtConfiguration()
                        .paymentJwtAuthenticationConverter()
                        .convert(jwt)
                        .getAuthorities())
                .extracting("authority")
                .contains("ROLE_CUSTOMER");
    }

    @Test
    void rejectsExpiredWrongIssuerSignatureAlgorithmAndRequiredClaims()
            throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048)
                .keyID("payment-test").generate();
        assertRejected(token(
                signingKey, "https://issuer.test",
                Instant.now().minusSeconds(1), true));
        assertRejected(token(
                signingKey, "https://wrong.test",
                Instant.now().plusSeconds(300), true));
        assertRejected(token(
                otherKey, "https://issuer.test",
                Instant.now().plusSeconds(300), true));
        assertRejected(hmacToken());
        assertRejected(token(
                signingKey, "https://issuer.test",
                Instant.now().plusSeconds(300), false));
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(Exception.class);
    }

    private static String token(
            RSAKey key, String issuer, Instant expiresAt, boolean requiredClaims)
            throws Exception {
        return sign(
                new RSASSASigner(key),
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(key.getKeyID()).build(),
                issuer, expiresAt, requiredClaims);
    }

    private static String hmacToken() throws Exception {
        byte[] secret =
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        return sign(
                new MACSigner(secret),
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID("payment-test").build(),
                "https://issuer.test", Instant.now().plusSeconds(300), true);
    }

    private static String sign(
            JWSSigner signer, JWSHeader header, String issuer,
            Instant expiresAt, boolean requiredClaims) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-id")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", List.of("CUSTOMER"));
        if (requiredClaims) {
            claims.claim("email", "person@example.com")
                    .claim("session_id", UUID.randomUUID().toString())
                    .claim("token_version", 0);
        }
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(signer);
        return jwt.serialize();
    }
}
