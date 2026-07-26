package com.orderflow.product.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.config.CatalogProperties;
import com.orderflow.product.error.ProductDomainException;
import com.orderflow.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogRulesTest {

    private final CatalogProperties properties =
            new CatalogProperties("usd", Set.of("usd", "eur"), 100);
    private final PriceCurrencyValidator prices = new PriceCurrencyValidator(properties);

    @Test
    void normalizesSlugsAndSku() {
        ProductRepository products = mock(ProductRepository.class);
        SlugService slugs = new SlugService(products);

        assertThat(slugs.normalize("  Café  Desk Lamp  ")).isEqualTo("cafe-desk-lamp");
        assertThat(new SkuNormalizer().normalize(" keyboard_01 "))
                .isEqualTo("KEYBOARD_01");
    }

    @Test
    void generatedSlugUsesDeterministicCollisionSuffix() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.existsBySlug("wireless-keyboard")).thenReturn(true);
        UUID id = UUID.fromString("12345678-0000-0000-0000-000000000000");

        assertThat(new SlugService(products).productSlug(null, "Wireless Keyboard", id))
                .isEqualTo("wireless-keyboard-12345678");
    }

    @Test
    void rejectsInvalidSku() {
        assertThatThrownBy(() -> new SkuNormalizer().normalize("bad sku!"))
                .isInstanceOf(ProductDomainException.class);
    }

    @Test
    void normalizesPriceAndCurrency() {
        assertThat(prices.price(new BigDecimal("49.9")))
                .isEqualByComparingTo("49.90");
        assertThat(prices.currency(" usd ")).isEqualTo("USD");
        assertThat(prices.currency(null)).isEqualTo("USD");
    }

    @Test
    void rejectsNegativeOrOverScaledPrice() {
        assertThatThrownBy(() -> prices.price(new BigDecimal("-0.01")))
                .isInstanceOfSatisfying(
                        ProductDomainException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_PRICE));
        assertThatThrownBy(() -> prices.price(new BigDecimal("1.001")))
                .isInstanceOf(ProductDomainException.class);
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> prices.currency("JPY"))
                .isInstanceOfSatisfying(
                        ProductDomainException.class,
                        exception ->
                                assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_CURRENCY));
    }

    @Test
    void validatesSortAndPaginationAllowlist() {
        ProductSearchValidator validator = new ProductSearchValidator(properties, prices);

        assertThat(validator.validate(
                                "keyboard",
                                null,
                                null,
                                null,
                                "USD",
                                "price,asc",
                                0,
                                20)
                        .sort())
                .isEqualTo("price,asc");
        assertThatThrownBy(() -> validator.validate(
                        null, null, null, null, null, "sku,asc", 0, 20))
                .isInstanceOfSatisfying(
                        ProductDomainException.class,
                        exception -> assertThat(exception.code()).isEqualTo(ErrorCode.INVALID_SORT));
        assertThatThrownBy(() -> validator.validate(
                        null, null, null, null, null, "name,asc", 0, 101))
                .isInstanceOf(ProductDomainException.class);
    }

    @Test
    void rejectsInvertedPriceRange() {
        ProductSearchValidator validator = new ProductSearchValidator(properties, prices);

        assertThatThrownBy(() -> validator.validate(
                        null,
                        null,
                        new BigDecimal("20"),
                        new BigDecimal("10"),
                        null,
                        null,
                        0,
                        20))
                .isInstanceOfSatisfying(
                        ProductDomainException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(ErrorCode.INVALID_PRICE_RANGE));
    }
}
