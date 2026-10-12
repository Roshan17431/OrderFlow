package com.orderflow.product.service;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.cache.CatalogVersionService;
import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.ProductStatus;
import com.orderflow.product.dto.CategoryResponse;
import com.orderflow.product.dto.CreateCategoryRequest;
import com.orderflow.product.dto.UpdateCategoryRequest;
import com.orderflow.product.dto.VersionRequest;
import com.orderflow.product.error.ProductDomainException;
import com.orderflow.product.repository.CategoryRepository;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.product.validation.SlugService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryManagementService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductMapper mapper;
    private final SlugService slugs;
    private final CatalogVersionService catalogVersion;
    private final Clock clock;

    public CategoryManagementService(
            CategoryRepository categories,
            ProductRepository products,
            ProductMapper mapper,
            SlugService slugs,
            CatalogVersionService catalogVersion,
            Clock clock) {
        this.categories = categories;
        this.products = products;
        this.mapper = mapper;
        this.slugs = slugs;
        this.catalogVersion = catalogVersion;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public com.orderflow.product.dto.PageResponse<CategoryResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProductDomainException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Page and size must be within the configured bounds.");
        }
        return com.orderflow.product.dto.PageResponse.from(categories.findAll(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("name"))).map(mapper::category), "name,asc");
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim().replaceAll("\\s+", " ");
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String slug = slugs.normalize(
                request.slug() == null || request.slug().isBlank() ? name : request.slug());
        if (categories.existsByNormalizedName(normalizedName)) {
            throw categoryConflict("The category name is already in use.");
        }
        if (categories.existsBySlug(slug)) {
            throw slugConflict();
        }
        Category category = Category.create(
                UUID.randomUUID(),
                name,
                slug,
                trimToNull(request.description()),
                Instant.now(clock));
        persist(category);
        catalogVersion.increment();
        return mapper.category(category);
    }

    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        Category category = requireCategory(categoryId);
        requireVersion(category, request.version());
        String name = request.name().trim().replaceAll("\\s+", " ");
        String slug = slugs.normalize(request.slug());
        if (!category.getSlug().equals(slug) && categories.existsBySlug(slug)) {
            throw slugConflict();
        }
        String normalizedName = name.toLowerCase(Locale.ROOT);
        if (!category.getNormalizedName().equals(normalizedName)
                && categories.existsByNormalizedName(normalizedName)) {
            throw categoryConflict("The category name is already in use.");
        }
        category.update(name, slug, trimToNull(request.description()), Instant.now(clock));
        persist(category);
        catalogVersion.increment();
        return mapper.category(category);
    }

    @Transactional
    public CategoryResponse activate(UUID categoryId, VersionRequest request) {
        Category category = requireCategory(categoryId);
        requireVersion(category, request.version());
        category.activate(Instant.now(clock));
        categories.saveAndFlush(category);
        catalogVersion.increment();
        return mapper.category(category);
    }

    @Transactional
    public CategoryResponse deactivate(UUID categoryId, VersionRequest request) {
        Category category = requireCategory(categoryId);
        requireVersion(category, request.version());
        if (products.existsByCategoryIdAndStatus(categoryId, ProductStatus.ACTIVE)) {
            throw new ProductDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CATEGORY_IN_USE,
                    "A category containing active products cannot be deactivated.");
        }
        category.deactivate(Instant.now(clock));
        categories.saveAndFlush(category);
        catalogVersion.increment();
        return mapper.category(category);
    }

    private Category requireCategory(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ProductDomainException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category was not found."));
    }

    private void requireVersion(Category category, long expected) {
        if (category.getVersion() != expected) {
            throw new ProductDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CATEGORY_VERSION_CONFLICT,
                    "The category was modified by another request.");
        }
    }

    private void persist(Category category) {
        try {
            categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            String message = rootMessage(exception);
            if (message.contains("uq_categories_slug")) {
                throw slugConflict();
            }
            throw categoryConflict("The category name is already in use.");
        }
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private ProductDomainException slugConflict() {
        return new ProductDomainException(
                HttpStatus.CONFLICT,
                ErrorCode.CATEGORY_SLUG_ALREADY_EXISTS,
                "The category slug is already in use.");
    }

    private ProductDomainException categoryConflict(String message) {
        return new ProductDomainException(
                HttpStatus.CONFLICT,
                ErrorCode.CATEGORY_SLUG_ALREADY_EXISTS,
                message);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
