package com.orderflow.product.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.catalog")
public record CatalogProperties(
        @NotBlank @DefaultValue("USD") String defaultCurrency,
        @NotEmpty @DefaultValue({"USD", "EUR", "GBP", "INR"}) Set<String> supportedCurrencies,
        @Min(1) @Max(100) @DefaultValue("100") int maxPageSize) {

    public CatalogProperties {
        defaultCurrency = normalize(defaultCurrency);
        supportedCurrencies = supportedCurrencies == null
                ? Set.of()
                : supportedCurrencies.stream()
                        .map(CatalogProperties::normalize)
                        .collect(Collectors.toUnmodifiableSet());
    }

    @AssertTrue(message = "default currency must be in the supported-currency list")
    public boolean isDefaultCurrencySupported() {
        return supportedCurrencies.contains(defaultCurrency);
    }

    @AssertTrue(message = "supported currencies must be ISO-style three-letter codes")
    public boolean areCurrenciesValid() {
        return supportedCurrencies.stream().allMatch(value -> value.matches("[A-Z]{3}"));
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
