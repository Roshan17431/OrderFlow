package com.orderflow.product.service;

import com.orderflow.platform.error.ErrorCode;
import com.orderflow.product.cache.CatalogCacheAccess;
import com.orderflow.product.cache.CatalogCacheKeys;
import com.orderflow.product.cache.CatalogCaches;
import com.orderflow.product.cache.CatalogVersionService;
import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.CategoryStatus;
import com.orderflow.product.domain.Product;
import com.orderflow.product.dto.CreateProductRequest;
import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductManagementResponse;
import com.orderflow.product.dto.ProductSearchCriteria;
import com.orderflow.product.dto.UpdateProductRequest;
import com.orderflow.product.dto.VersionRequest;
import com.orderflow.product.error.ProductDomainException;
import com.orderflow.product.repository.CategoryRepository;
import com.orderflow.product.repository.ProductRepository;
import com.orderflow.product.repository.ProductSpecifications;
import com.orderflow.product.security.RequestIdentity;
import com.orderflow.product.validation.PriceCurrencyValidator;
import com.orderflow.product.validation.ProductSearchValidator;
import com.orderflow.product.validation.SkuNormalizer;
import com.orderflow.product.validation.SlugService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductManagementService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductMapper mapper;
    private final SkuNormalizer skuNormalizer;
    private final SlugService slugs;
    private final PriceCurrencyValidator prices;
    private final ProductSearchValidator search;
    private final CatalogCacheAccess cache;
    private final CatalogCacheKeys cacheKeys;
    private final CatalogVersionService catalogVersion;
    private final Clock clock;

    public ProductManagementService(
            ProductRepository products,
            CategoryRepository categories,
            ProductMapper mapper,
            SkuNormalizer skuNormalizer,
            SlugService slugs,
            PriceCurrencyValidator prices,
            ProductSearchValidator search,
            CatalogCacheAccess cache,
            CatalogCacheKeys cacheKeys,
            CatalogVersionService catalogVersion,
            Clock clock) {
        this.products = products;
        this.categories = categories;
        this.mapper = mapper;
        this.skuNormalizer = skuNormalizer;
        this.slugs = slugs;
        this.prices = prices;
        this.search = search;
        this.cache = cache;
        this.cacheKeys = cacheKeys;
        this.catalogVersion = catalogVersion;
        this.clock = clock;
    }

    @Transactional
    public ProductManagementResponse create(
            RequestIdentity identity, CreateProductRequest request) {
        UUID vendorId = resolveVendor(identity, request.vendorId());
        String sku = skuNormalizer.normalize(request.sku());
        if (products.existsByVendorIdAndSku(vendorId, sku)) {
            throw conflict(ErrorCode.SKU_ALREADY_EXISTS, "The vendor SKU is already in use.");
        }
        UUID productId = UUID.randomUUID();
        String slug = slugs.productSlug(request.slug(), request.name(), productId);
        Category category = requireCategory(request.categoryId());
        Product product = Product.draft(
                productId,
                vendorId,
                sku,
                slug,
                request.name().trim(),
                trimToNull(request.description()),
                prices.price(request.priceAmount()),
                prices.currency(request.currency()),
                category,
                Instant.now(clock));
        persist(product);
        invalidate(product);
        return mapper.management(product);
    }

    @Transactional
    public ProductManagementResponse update(
            RequestIdentity identity, UUID productId, UpdateProductRequest request) {
        Product product = requireOwned(identity, productId);
        requireVersion(product.getVersion(), request.version());
        Category category = requireCategory(request.categoryId());
        product.update(
                request.name().trim(),
                trimToNull(request.description()),
                prices.price(request.priceAmount()),
                prices.currency(request.currency()),
                category,
                Instant.now(clock));
        persist(product);
        invalidate(product);
        return mapper.management(product);
    }

    @Transactional
    public ProductManagementResponse activate(
            RequestIdentity identity, UUID productId, VersionRequest request) {
        Product product = requireOwned(identity, productId);
        requireVersion(product.getVersion(), request.version());
        if (product.getCategory().getStatus() != CategoryStatus.ACTIVE) {
            throw new ProductDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.PRODUCT_NOT_PURCHASABLE,
                    "A product in an inactive category cannot be activated.");
        }
        transition(() -> product.activate(Instant.now(clock)));
        persist(product);
        invalidate(product);
        return mapper.management(product);
    }

    @Transactional
    public ProductManagementResponse deactivate(
            RequestIdentity identity, UUID productId, VersionRequest request) {
        Product product = requireOwned(identity, productId);
        requireVersion(product.getVersion(), request.version());
        transition(() -> product.deactivate(Instant.now(clock)));
        persist(product);
        invalidate(product);
        return mapper.management(product);
    }

    @Transactional
    public ProductManagementResponse archive(
            RequestIdentity identity, UUID productId, VersionRequest request) {
        Product product = requireOwned(identity, productId);
        requireVersion(product.getVersion(), request.version());
        product.archive(Instant.now(clock));
        persist(product);
        invalidate(product);
        return mapper.management(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductManagementResponse> list(
            RequestIdentity identity, UUID requestedVendorId, int page, int size, String sort) {
        ProductSearchCriteria validated =
                search.validate(null, null, null, null, null, sort, page, size);
        UUID vendorId;
        if (identity.admin()) {
            vendorId = requestedVendorId;
        } else {
            if (requestedVendorId != null && !identity.userId().equals(requestedVendorId)) {
                throw notFound();
            }
            vendorId = identity.userId();
        }
        Page<Product> result = vendorId == null
                ? products.findAll(
                        (Specification<Product>) null, search.pageable(validated))
                : products.findAll(
                        ProductSpecifications.vendorProducts(vendorId),
                        search.pageable(validated));
        return PageResponse.from(result.map(mapper::management), validated.sort());
    }

    private UUID resolveVendor(RequestIdentity identity, UUID requested) {
        if (identity.admin()) {
            return requested == null ? identity.userId() : requested;
        }
        if (requested != null && !identity.userId().equals(requested)) {
            throw new ProductDomainException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.INSUFFICIENT_PERMISSIONS,
                    "A vendor cannot create products for another owner.");
        }
        return identity.userId();
    }

    private Product requireOwned(RequestIdentity identity, UUID productId) {
        Product product = products.findManagementById(productId).orElseThrow(this::notFound);
        if (!identity.admin() && !identity.userId().equals(product.getVendorId())) {
            throw notFound();
        }
        return product;
    }

    private Category requireCategory(UUID categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ProductDomainException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category was not found."));
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw conflict(
                    ErrorCode.PRODUCT_VERSION_CONFLICT,
                    "The product was modified by another request.");
        }
    }

    private void transition(Runnable transition) {
        try {
            transition.run();
        } catch (IllegalStateException exception) {
            throw conflict(
                    ErrorCode.PRODUCT_NOT_ACTIVE,
                    "Archived products cannot return to an active lifecycle.");
        }
    }

    private void persist(Product product) {
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException exception) {
            String detail = rootMessage(exception);
            if (detail.contains("uq_products_vendor_sku")) {
                throw conflict(ErrorCode.SKU_ALREADY_EXISTS, "The vendor SKU is already in use.");
            }
            throw conflict(
                    ErrorCode.PRODUCT_SLUG_ALREADY_EXISTS,
                    "The product slug is already in use.");
        }
    }

    private void invalidate(Product product) {
        cache.evict(CatalogCaches.PRODUCT_DETAIL, cacheKeys.detail(product.getId()));
        cache.evict(CatalogCaches.PRODUCT_SLUG, cacheKeys.slug(product.getSlug()));
        catalogVersion.increment();
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ProductDomainException notFound() {
        return new ProductDomainException(
                HttpStatus.NOT_FOUND,
                ErrorCode.PRODUCT_NOT_FOUND,
                "Product was not found.");
    }

    private ProductDomainException conflict(ErrorCode code, String message) {
        return new ProductDomainException(HttpStatus.CONFLICT, code, message);
    }
}
