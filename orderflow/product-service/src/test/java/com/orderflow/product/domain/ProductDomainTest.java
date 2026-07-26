package com.orderflow.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductDomainTest {

    @Test
    void productMovesThroughLifecycleAndCannotLeaveArchive() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Category category =
                Category.create(UUID.randomUUID(), "Books", "books", null, now);
        Product product = Product.draft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BOOK-1",
                "a-book",
                "A Book",
                null,
                new BigDecimal("10.00"),
                "USD",
                category,
                now);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        product.activate(now.plusSeconds(1));
        assertThat(product.isPubliclyVisible()).isTrue();
        product.deactivate(now.plusSeconds(2));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        product.archive(now.plusSeconds(3));
        assertThatThrownBy(() -> product.activate(now.plusSeconds(4)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inactiveCategoryMakesActiveProductPrivate() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Category category =
                Category.create(UUID.randomUUID(), "Books", "books", null, now);
        Product product = Product.draft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BOOK-1",
                "a-book",
                "A Book",
                null,
                BigDecimal.ONE,
                "USD",
                category,
                now);
        product.activate(now);
        category.deactivate(now);

        assertThat(product.isPubliclyVisible()).isFalse();
    }

    @Test
    void categoryNameIsNormalized() {
        Category category = Category.create(
                UUID.randomUUID(),
                " Home   And Kitchen ",
                "home-and-kitchen",
                null,
                Instant.now());

        assertThat(category.getNormalizedName()).isEqualTo("home and kitchen");
    }
}
