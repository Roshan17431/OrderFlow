package com.orderflow.product.repository;

import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.CategoryStatus;
import com.orderflow.product.domain.Product;
import com.orderflow.product.domain.ProductStatus;
import com.orderflow.product.dto.ProductSearchCriteria;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> publicCatalog(ProductSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            Join<Product, Category> category = root.join("category", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ProductStatus.ACTIVE));
            predicates.add(criteriaBuilder.equal(category.get("status"), CategoryStatus.ACTIVE));
            if (criteria.query() != null) {
                String pattern = "%" + criteria.query().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), pattern),
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("description")), pattern)));
            }
            if (criteria.category() != null) {
                predicates.add(criteriaBuilder.equal(category.get("slug"), criteria.category()));
            }
            if (criteria.minPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("priceAmount"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("priceAmount"), criteria.maxPrice()));
            }
            if (criteria.currency() != null) {
                predicates.add(criteriaBuilder.equal(root.get("currency"), criteria.currency()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Product> vendorProducts(UUID vendorId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("vendorId"), vendorId);
    }
}
