package com.orderflow.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.auth.config.JwtProperties;
import com.orderflow.auth.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtServiceTest {

    private JwtProperties properties;
    private JwtService service;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(
                "https://issuer.test",
                "test-key",
                Duration.ofMinutes(10),
                true,
                null,
                null,
                2048);
        RsaKeyProvider keys = new RsaKeyProvider(properties, new DefaultResourceLoader());
        JwtConfiguration configuration = new JwtConfiguration();
        JwtEncoder encoder = configuration.jwtEncoder(keys, properties);
        decoder = configuration.jwtDecoder(keys, properties);
        service = new JwtService(encoder, properties);
    }

    @Test
    void issuesRs256TokenWithRequiredClaimsAndKid() {
        User user = User.registeredCustomer(
                "person@example.com",
                "hash",
                "Person",
                Instant.now());
        UUID sessionId = UUID.randomUUID();

        IssuedAccessToken issued = service.issue(user, sessionId);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo("test-key");
        assertThat(jwt.getIssuer().toString()).isEqualTo(properties.issuer());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getId()).isEqualTo(issued.jti());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getClaimAsString("session_id")).isEqualTo(sessionId.toString());
        assertThat(((Number) jwt.getClaim("token_version")).longValue()).isZero();
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void publicJwkContainsNoPrivateParameters() {
        RsaKeyProvider keys = new RsaKeyProvider(properties, new DefaultResourceLoader());
        var jwks = new com.orderflow.auth.controller.JwkController(keys, properties).jwks();
        String serialized = jwks.toString();
        assertThat(serialized)
                .contains("kty=RSA", "alg=RS256", "kid=test-key")
                .doesNotContain(" d=", "p=", "q=");
    }
}
