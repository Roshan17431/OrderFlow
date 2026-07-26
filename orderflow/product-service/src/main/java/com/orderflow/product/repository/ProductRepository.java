package com.orderflow.product.repository;

import com.orderflow.product.domain.Product;
import com.orderflow.product.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.domain.Specification;

public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @Query("""
            select p from Product p
            join fetch p.category c
            where p.id = :id
              and p.status = com.orderflow.product.domain.ProductStatus.ACTIVE
              and c.status = com.orderflow.product.domain.CategoryStatus.ACTIVE
            """)
    Optional<Product> findPublicById(@Param("id") UUID id);

    @Query("""
            select p from Product p
            join fetch p.category c
            where p.slug = :slug
              and p.status = com.orderflow.product.domain.ProductStatus.ACTIVE
              and c.status = com.orderflow.product.domain.CategoryStatus.ACTIVE
            """)
    Optional<Product> findPublicBySlug(@Param("slug") String slug);

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findManagementById(@Param("id") UUID id);

    @Query("select distinct p from Product p join fetch p.category where p.id in :ids")
    List<Product> findAllResolvedByIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsByVendorIdAndSku(UUID vendorId, String sku);

    boolean existsBySlug(String slug);

    boolean existsByCategoryIdAndStatus(UUID categoryId, ProductStatus status);
}
