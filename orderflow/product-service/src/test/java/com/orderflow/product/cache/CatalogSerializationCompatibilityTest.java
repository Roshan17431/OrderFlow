package com.orderflow.product.cache;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.orderflow.product.dto.*;

class CatalogSerializationCompatibilityTest {
    @Test
    void readsBoot3TypedCatalogValuesAndBoot3ReadsNewValues() throws Exception {
        var serializer = CatalogCacheConfiguration.valueSerializer(
                JsonMapper.builder().configureForJackson2()
                    .disable(tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build());
        var old = org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .defaultTyping(true).build();
        for (String name : java.util.List.of("catalog-product.json", "catalog-categories.json")) {
            try (var input = getClass().getResourceAsStream("/compat/boot3/" + name)) {
                assertThat(input).isNotNull();
                byte[] bytes = input.readAllBytes();
                Object existing = serializer.deserialize(bytes);
                assertThat(existing).isEqualTo(old.deserialize(bytes));
                assertThat(old.deserialize(serializer.serialize(existing))).isEqualTo(existing);
            }
        }
    }
    @Test
    void rejectsUnapprovedPolymorphicTypes() {
        var serializer = CatalogCacheConfiguration.valueSerializer(new JsonMapper());
        assertThatThrownBy(() -> serializer.deserialize(
                "{\"@class\":\"java.io.File\",\"path\":\"/tmp/untrusted\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(org.springframework.data.redis.serializer.SerializationException.class);
    }
}
