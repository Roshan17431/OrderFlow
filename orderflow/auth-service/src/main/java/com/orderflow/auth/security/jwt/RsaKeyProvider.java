package com.orderflow.auth.security.jwt;

import com.orderflow.auth.config.JwtProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class RsaKeyProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RsaKeyProvider.class);
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    public RsaKeyProvider(JwtProperties properties, ResourceLoader resourceLoader) {
        try {
            KeyPair pair = properties.allowEphemeralKeys()
                    ? generate(properties.minimumKeySize())
                    : load(
                            resourceLoader.getResource(properties.privateKeyLocation()),
                            resourceLoader.getResource(properties.publicKeyLocation()));
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            if (publicKey.getModulus().bitLength() < properties.minimumKeySize()) {
                throw new IllegalStateException("Configured RSA key is smaller than the minimum");
            }
            if (properties.allowEphemeralKeys()) {
                LOGGER.warn(
                        "Using an ephemeral RSA key for local development; tokens become invalid "
                                + "when auth-service restarts");
            }
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize RSA JWT keys", exception);
        }
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    private KeyPair generate(int keySize) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    private KeyPair load(Resource privateResource, Resource publicResource)
            throws IOException, GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        byte[] privateBytes = decodePem(
                privateResource,
                "-----BEGIN PRIVATE KEY-----",
                "-----END PRIVATE KEY-----");
        byte[] publicBytes = decodePem(
                publicResource,
                "-----BEGIN PUBLIC KEY-----",
                "-----END PUBLIC KEY-----");
        return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(publicBytes)),
                factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
    }

    private byte[] decodePem(Resource resource, String begin, String end) throws IOException {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        String encoded = pem.replace(begin, "")
                .replace(end, "")
                .replaceAll("\\s", "");
        if (encoded.isBlank()) {
            throw new IllegalStateException("Configured PEM resource is empty");
        }
        return Base64.getDecoder().decode(encoded);
    }
}
