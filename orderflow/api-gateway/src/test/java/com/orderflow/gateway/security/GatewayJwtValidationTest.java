package com.orderflow.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.orderflow.gateway.config.GatewaySecurityProperties;
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
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

class GatewayJwtValidationTest {

    private static HttpServer server;
    private static RSAKey signingKey;
    private static ReactiveJwtDecoder decoder;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("gateway-test").generate();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] response = new JWKSet(signingKey.toPublicJWK())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        GatewaySecurityProperties properties = new GatewaySecurityProperties(
                "https://issuer.test",
                "http://localhost:" + server.getAddress().getPort() + "/jwks");
        decoder = new GatewaySecurityConfiguration().reactiveJwtDecoder(properties);
    }

    @AfterAll
    static void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsValidRs256TokenWithCorrectIssuer() throws Exception {
        StepVerifier.create(decoder.decode(token(
                        signingKey,
                        "https://issuer.test",
                        Instant.now().plusSeconds(300))))
                .assertNext(jwt -> {
                    assertThat(jwt.getSubject()).isEqualTo("user-id");
                    assertThat(jwt.getClaimAsStringList("roles")).contains("CUSTOMER");
                })
                .verifyComplete();
    }

    @Test
    void rejectsExpiredWrongIssuerInvalidSignatureAndWrongAlgorithm() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("gateway-test").generate();
        StepVerifier.create(decoder.decode(token(
                        signingKey,
                        "https://issuer.test",
                        Instant.now().minusSeconds(1))))
                .expectError()
                .verify();
        StepVerifier.create(decoder.decode(token(
                        signingKey,
                        "https://wrong.test",
                        Instant.now().plusSeconds(300))))
                .expectError()
                .verify();
        StepVerifier.create(decoder.decode(token(
                        otherKey,
                        "https://issuer.test",
                        Instant.now().plusSeconds(300))))
                .expectError()
                .verify();
        StepVerifier.create(decoder.decode(hmacToken()))
                .expectError()
                .verify();
    }

    @Test
    void mapsRolesToSpringRoleAuthorities() throws Exception {
        var jwt = decoder.decode(token(
                signingKey,
                "https://issuer.test",
                Instant.now().plusSeconds(300)));
        StepVerifier.create(jwt.flatMap(new GatewaySecurityConfiguration()
                        .gatewayJwtAuthenticationConverter()::convert))
                .assertNext(authentication -> assertThat(authentication.getAuthorities())
                        .extracting("authority")
                        .contains("ROLE_CUSTOMER"))
                .verifyComplete();
    }

    private static String token(RSAKey key, String issuer, Instant expiresAt)
            throws Exception {
        return sign(
                new RSASSASigner(key),
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                issuer,
                expiresAt);
    }

    private static String hmacToken() throws Exception {
        byte[] secret = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        return sign(
                new MACSigner(secret),
                new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("gateway-test").build(),
                "https://issuer.test",
                Instant.now().plusSeconds(300));
    }

    private static String sign(
            JWSSigner signer,
            JWSHeader header,
            String issuer,
            Instant expiresAt) throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                header,
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .subject("user-id")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(expiresAt))
                        .jwtID(UUID.randomUUID().toString())
                        .claim("email", "person@example.com")
                        .claim("roles", List.of("CUSTOMER"))
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0)
                        .build());
        jwt.sign(signer);
        return jwt.serialize();
    }
}
