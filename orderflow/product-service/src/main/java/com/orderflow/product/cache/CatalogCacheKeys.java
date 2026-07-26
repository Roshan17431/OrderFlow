package com.orderflow.product.cache;

import com.orderflow.product.dto.ProductSearchCriteria;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CatalogCacheKeys {

    private final CatalogVersionService versions;

    public CatalogCacheKeys(CatalogVersionService versions) {
        this.versions = versions;
    }

    public String detail(UUID productId) {
        return "id:" + productId;
    }

    public String slug(String slug) {
        return "slug:" + slug;
    }

    public String categories() {
        return "catalog:" + versions.current();
    }

    public String search(ProductSearchCriteria criteria) {
        return "catalog:" + versions.current() + ":" + sha256(criteria.toString());
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
