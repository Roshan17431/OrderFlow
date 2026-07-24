package com.orderflow.platform.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrelationIdValidatorTest {

    @Test
    void preservesAndTrimsValidValue() {
        assertThat(CorrelationIdValidator.validateOrGenerate("  order_42:attempt.1  "))
                .isEqualTo("order_42:attempt.1");
    }

    @Test
    void generatesForAbsentValue() {
        assertThat(UUID.fromString(CorrelationIdValidator.validateOrGenerate(null))).isNotNull();
    }

    @Test
    void generatesForBlankValue() {
        assertThat(UUID.fromString(CorrelationIdValidator.validateOrGenerate(" \t "))).isNotNull();
    }

    @Test
    void generatesForOversizedValue() {
        String oversized = "a".repeat(CorrelationIdValidator.MAX_LENGTH + 1);
        assertThat(UUID.fromString(CorrelationIdValidator.validateOrGenerate(oversized))).isNotNull();
    }

    @Test
    void generatesForInvalidCharacters() {
        assertThat(UUID.fromString(CorrelationIdValidator.validateOrGenerate("unsafe\r\nvalue"))).isNotNull();
        assertThat(UUID.fromString(CorrelationIdValidator.validateOrGenerate("spaces are unsafe"))).isNotNull();
    }
}
