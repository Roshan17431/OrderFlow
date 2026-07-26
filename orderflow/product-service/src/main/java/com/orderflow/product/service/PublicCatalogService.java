package com.orderflow.product.service;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.cache.CatalogCacheAccess;
import com.orderflow.product.cache.CatalogCacheKeys;
import com.orderflow.product.cache.CatalogCaches;
import com.orderflow.product.domain.CategoryStatus;
import com.orderflow.product.domain.Product;
import com.orderflow.product.dto.CategoryListResponse;
import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductDetailResponse;
import com.orderflow.product.dto.ProductResolutionItem;
import com.orderflow.product.dto.ProductResolutionRequest;
import com.orderflow.product.dto.ProductResolutionResponse;
import com.orderflow.product.dto.ProductSearchCriteria;
import com.orderflow.product.dto.ProductSummary;
import com.orderflow.product.error.ProductDomainException;
import com.orderflow.product.repository.CategoryRepository;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.product.repository.ProductSpecifications;
import com.orderflow.product.validation.ProductSearchValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCatalogService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductMapper mapper;
    private final ProductSearchValidator searchValidator;
    private final CatalogCacheAccess cache;
    private final CatalogCacheKeys cacheKeys;
    private final Counter searchRequests;

    public PublicCatalogService(
            ProductRepository products,
            CategoryRepository categories,
            ProductMapper mapper,
            ProductSearchValidator searchValidator,
            CatalogCacheAccess cache,
            CatalogCacheKeys cacheKeys,
            MeterRegistry registry) {
        this.products = products;
        this.categories = categories;
        this.mapper = mapper;
        this.searchValidator = searchValidator;
        this.cache = cache;
        this.cacheKeys = cacheKeys;
        this.searchRequests = registry.counter("orderflow.catalog.search.requests");
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public PageResponse<ProductSummary> search(ProductSearchCriteria criteria) {
        searchRequests.increment();
        return (PageResponse<ProductSummary>) cache.get(
                CatalogCaches.PRODUCT_SEARCH,
                cacheKeys.search(criteria),
                PageResponse.class,
                () -> {
                    Page<ProductSummary> page = products.findAll(
                                    ProductSpecifications.publicCatalog(criteria),
                                    searchValidator.pageable(criteria))
                            .map(mapper::summary);
                    return PageResponse.from(page, criteria.sort());
                });
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detail(UUID productId) {
        return cache.get(
                CatalogCaches.PRODUCT_DETAIL,
                cacheKeys.detail(productId),
                ProductDetailResponse.class,
                () -> mapper.detail(products.findPublicById(productId)
                        .orElseThrow(this::notFound)));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detailBySlug(String slug) {
        String normalized = slug.trim().toLowerCase();
        return cache.get(
                CatalogCaches.PRODUCT_SLUG,
                cacheKeys.slug(normalized),
                ProductDetailResponse.class,
                () -> mapper.detail(products.findPublicBySlug(normalized)
                        .orElseThrow(this::notFound)));
    }

    @Transactional(readOnly = true)
    public CategoryListResponse categories() {
        return cache.get(
                CatalogCaches.CATEGORY_LIST,
                cacheKeys.categories(),
                CategoryListResponse.class,
                () -> new CategoryListResponse(categories
                        .findAllByStatusOrderByNameAsc(CategoryStatus.ACTIVE)
                        .stream()
                        .map(mapper::category)
                        .toList()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> categoryProducts(
            String slug, ProductSearchCriteria requested) {
        String normalized = slug.trim().toLowerCase();
        categories.findBySlugAndStatus(normalized, CategoryStatus.ACTIVE)
                .orElseThrow(() -> new ProductDomainException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category was not found."));
        ProductSearchCriteria criteria = new ProductSearchCriteria(
                requested.query(),
                normalized,
                requested.minPrice(),
                requested.maxPrice(),
                requested.currency(),
                requested.sort(),
                requested.page(),
                requested.size());
        return search(criteria);
    }

    @Transactional(readOnly = true)
    public ProductResolutionResponse resolve(ProductResolutionRequest request) {
        LinkedHashSet<UUID> requested = new LinkedHashSet<>(request.productIds());
        Map<UUID, Product> resolved = products.findAllResolvedByIdIn(requested).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ProductResolutionItem> items = requested.stream()
                .map(productId -> resolution(productId, resolved.get(productId)))
                .toList();
        return new ProductResolutionResponse(items);
    }

    private ProductResolutionItem resolution(UUID productId, Product product) {
        if (product == null || !product.isPubliclyVisible()) {
            return new ProductResolutionItem(productId, null, null, null, false, null);
        }
        return new ProductResolutionItem(
                productId,
                product.getName(),
                product.getPriceAmount(),
                product.getCurrency(),
                true,
                product.getVersion());
    }

    private ProductDomainException notFound() {
        return new ProductDomainException(
                HttpStatus.NOT_FOUND,
                ErrorCode.PRODUCT_NOT_FOUND,
                "Product was not found.");
    }
}
