package com.orderflow.product.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.product.controller.AdminCategoryController;
import com.orderflow.product.controller.CategoryController;
import com.orderflow.product.controller.PublicCatalogController;
import com.orderflow.product.controller.VendorProductController;
import com.orderflow.product.correlation.CorrelationIdFilter;
import com.orderflow.product.dto.CategoryListResponse;
import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductSearchCriteria;
import com.orderflow.product.service.CategoryManagementService;
import com.orderflow.product.service.ProductManagementService;
import com.orderflow.product.service.PublicCatalogService;
import com.orderflow.product.validation.ProductSearchValidator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    PublicCatalogController.class,
    CategoryController.class,
    VendorProductController.class,
    AdminCategoryController.class
})
@Import({
    ProductSecurityConfiguration.class,
    CorrelationIdFilter.class,
    ApiSecurityErrorWriter.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    RevocationValidationFilter.class,
    ProductSecurityMvcTest.SecurityBeans.class
})
class ProductSecurityMvcTest {

    private static final UUID USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean
    private PublicCatalogService catalog;

    @MockBean
    private ProductManagementService products;

    @MockBean
    private CategoryManagementService categories;

    @MockBean
    private ProductSearchValidator search;

    @MockBean
    private RevocationStateService revocations;

    @MockBean
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria(null, null, null, null, null, "createdAt,desc", 0, 20);
        when(search.validate(
                        any(), any(), any(), any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(criteria);
        when(catalog.search(criteria))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, "createdAt,desc"));
        when(catalog.categories()).thenReturn(new CategoryListResponse(List.of()));
        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
    }

    @Test
    void publicCatalogEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenUsesApiErrorAndCorrelationId() throws Exception {
        mockMvc.perform(post("/api/products/resolve")
                        .header("X-Correlation-ID", "product-security-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"" + USER_ID + "\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "product-security-test"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.correlationId").value("product-security-test"));
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/vendor/products")
                        .with(token("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));
    }

    @Test
    void vendorCanCreateProduct() throws Exception {
        mockMvc.perform(post("/api/vendor/products")
                        .with(token("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAdminCanManageCategories() throws Exception {
        String body = "{\"name\":\"Garden\",\"slug\":\"garden\",\"description\":\"Garden\"}";
        mockMvc.perform(post("/api/admin/categories")
                        .with(token("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/categories")
                        .with(token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void revokedAndStaleTokensAreRejected() throws Exception {
        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.REVOKED);
        mockMvc.perform(post("/api/vendor/products")
                        .with(token("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.STALE);
        mockMvc.perform(post("/api/vendor/products")
                        .with(token("VENDOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor token(String role) {
        return jwt()
                .jwt(builder -> builder
                        .subject(USER_ID.toString())
                        .jti(UUID.randomUUID().toString())
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(600))
                        .claim("email", "user@example.com")
                        .claim("session_id", UUID.randomUUID().toString())
                        .claim("token_version", 0L)
                        .claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String createBody() {
        return """
                {
                  "sku":"TEST-1",
                  "name":"Test Product",
                  "description":"Test",
                  "priceAmount":10.00,
                  "currency":"USD",
                  "categoryId":"10000000-0000-0000-0000-000000000001"
                }
                """;
    }

    @TestConfiguration
    static class SecurityBeans {

        @Bean
        Converter<Jwt, AbstractAuthenticationToken> productJwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }
}
