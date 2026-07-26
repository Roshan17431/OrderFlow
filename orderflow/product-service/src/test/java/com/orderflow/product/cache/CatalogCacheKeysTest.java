package com.orderflow.product.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orderflow.product.dto.ProductSearchCriteria;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogCacheKeysTest {

    @Test
    void keysAreStableNamespacedInputsWithoutAuthenticationData() {
        CatalogVersionService versions = mock(CatalogVersionService.class);
        when(versions.current()).thenReturn(7L);
        CatalogCacheKeys keys = new CatalogCacheKeys(versions);
        ProductSearchCriteria criteria =
                new ProductSearchCriteria("book", null, null, null, "USD", "name,asc", 0, 20);

        assertThat(keys.detail(UUID.fromString("10000000-0000-0000-0000-000000000001")))
                .isEqualTo("id:10000000-0000-0000-0000-000000000001");
        assertThat(keys.categories()).isEqualTo("catalog:7");
        assertThat(keys.search(criteria))
                .startsWith("catalog:7:")
                .hasSize("catalog:7:".length() + 64);
    }
}
