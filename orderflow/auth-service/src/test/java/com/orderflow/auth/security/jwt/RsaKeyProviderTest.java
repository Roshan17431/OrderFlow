package com.orderflow.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.auth.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class RsaKeyProviderTest {

    @TempDir
    static Path temporaryDirectory;

    private static KeyPair configuredPair;
    private static Path privateKeyFile;
    private static Path publicKeyFile;

    @BeforeAll
    static void createTestOnlyKeyFiles() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        configuredPair = generator.generateKeyPair();
        privateKeyFile = temporaryDirectory.resolve("test-private.pem");
        publicKeyFile = temporaryDirectory.resolve("test-public.pem");
        Files.writeString(
                privateKeyFile,
                pem("PRIVATE KEY", configuredPair.getPrivate().getEncoded()),
                StandardCharsets.US_ASCII);
        Files.writeString(
                publicKeyFile,
                pem("PUBLIC KEY", configuredPair.getPublic().getEncoded()),
                StandardCharsets.US_ASCII);
    }

    @Test
    void loadsConfiguredPkcs8PrivateAndX509PublicPemKeys() {
        JwtProperties properties = new JwtProperties(
                "https://issuer.test",
                "configured-key",
                Duration.ofMinutes(10),
                false,
                privateKeyFile.toUri().toString(),
                publicKeyFile.toUri().toString(),
                2048);

        RsaKeyProvider provider =
                new RsaKeyProvider(properties, new DefaultResourceLoader());

        assertThat(provider.privateKey().getEncoded())
                .isEqualTo(configuredPair.getPrivate().getEncoded());
        assertThat(provider.publicKey().getEncoded())
                .isEqualTo(configuredPair.getPublic().getEncoded());
        assertThat(provider.publicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }
}
