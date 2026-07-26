package com.orderflow.product.validation;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.error.ProductDomainException;
import com.orderflow.product.repository.ProductRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SlugService {

    private static final int MAX_LENGTH = 120;
    private final ProductRepository products;

    public SlugService(ProductRepository products) {
        this.products = products;
    }

    public String productSlug(String requested, String name, UUID productId) {
        if (requested != null && !requested.isBlank()) {
            String normalized = normalize(requested);
            if (products.existsBySlug(normalized)) {
                throw new ProductDomainException(
                        HttpStatus.CONFLICT,
                        ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS,
                        "The product slug is already in use.");
            }
            return normalized;
        }
        String base = normalize(name);
        if (!products.existsBySlug(base)) {
            return base;
        }
        String suffix = "-" + productId.toString().substring(0, 8);
        String candidate = truncate(base, MAX_LENGTH - suffix.length()) + suffix;
        if (products.existsBySlug(candidate)) {
            throw new ProductDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS,
                    "A unique product slug could not be generated.");
        }
        return candidate;
    }

    public String normalize(String value) {
        String ascii = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String normalized = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "A valid slug could not be generated.");
        }
        return truncate(normalized, MAX_LENGTH).replaceAll("-+$", "");
    }

    private String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
