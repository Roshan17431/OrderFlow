package com.orderflow.product.validation;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.config.CatalogProperties;
import com.orderflow.product.dto.ProductSearchCriteria;
import com.orderflow.product.error.ProductDomainException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchValidator {

    public static final String DEFAULT_SORT = "createdAt,desc";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "price", "priceAmount",
            "name", "name");
    private final CatalogProperties properties;
    private final PriceCurrencyValidator priceCurrency;

    public ProductSearchValidator(
            CatalogProperties properties,
            PriceCurrencyValidator priceCurrency) {
        this.properties = properties;
        this.priceCurrency = priceCurrency;
    }

    public ProductSearchCriteria validate(
            String query,
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String currency,
            String sort,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > properties.maxPageSize()) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Page and size must be within the configured bounds.");
        }
        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_PRICE_RANGE,
                    "The requested price range is invalid.");
        }
        String selectedSort = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
        parseSort(selectedSort);
        String normalizedCurrency =
                currency == null || currency.isBlank() ? null : priceCurrency.currency(currency);
        return new ProductSearchCriteria(
                trimToNull(query),
                category == null ? null : category.trim().toLowerCase(Locale.ROOT),
                minPrice,
                maxPrice,
                normalizedCurrency,
                selectedSort,
                page,
                size);
    }

    public Pageable pageable(ProductSearchCriteria criteria) {
        return PageRequest.of(criteria.page(), criteria.size(), parseSort(criteria.sort()));
    }

    public Sort parseSort(String value) {
        String[] parts = value.split(",", -1);
        String property = parts.length == 2 ? SORT_FIELDS.get(parts[0]) : null;
        if (property == null
                || !("asc".equals(parts[1]) || "desc".equals(parts[1]))) {
            throw new ProductDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_SORT,
                    "The requested product sort is not supported.");
        }
        return Sort.by("asc".equals(parts[1])
                ? Sort.Order.asc(property)
                : Sort.Order.desc(property));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
