package com.orderflow.product.validation;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.error.ProductDomainException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SkuNormalizer {

    public String normalize(String value) {
        String sku = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (sku.isBlank() || sku.length() > 64 || !sku.matches("[A-Z0-9._-]+")) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "SKU must contain only letters, digits, periods, underscores, and hyphens.");
        }
        return sku;
    }
}
