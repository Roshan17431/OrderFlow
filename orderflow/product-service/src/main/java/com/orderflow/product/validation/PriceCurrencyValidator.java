package com.orderflow.product.validation;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.config.CatalogProperties;
import com.orderflow.product.error.ProductDomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PriceCurrencyValidator {

    private final CatalogProperties properties;

    public PriceCurrencyValidator(CatalogProperties properties) {
        this.properties = properties;
    }

    public BigDecimal price(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision() > 19 || value.scale() > 2) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_PRICE,
                    "Price must be non-negative with at most two decimal places.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public String currency(String value) {
        String currency = value == null || value.isBlank()
                ? properties.defaultCurrency()
                : value.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")
                || !properties.supportedCurrencies().contains(currency)) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_CURRENCY,
                    "The requested currency is not supported.");
        }
        return currency;
    }
}
