package com.orderflow.product.repository;

import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.CategoryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByStatusOrderByNameAsc(CategoryStatus status);

    Optional<Category> findBySlugAndStatus(String slug, CategoryStatus status);

    boolean existsBySlug(String slug);

    boolean existsByNormalizedName(String normalizedName);
}
