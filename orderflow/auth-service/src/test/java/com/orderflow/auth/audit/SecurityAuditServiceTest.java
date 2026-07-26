package com.orderflow.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityAuditServiceTest {

    private final SecurityAuditService service = new SecurityAuditService();

    @Test
    void sanitizesControlCharactersAndRejectsNonHashes() {
        assertThat(service.sanitize("reason\r\nforged")).isEqualTo("reason__forged");
        assertThat(service.sanitizeHash("127.0.0.1")).isNull();
        assertThat(service.sanitizeHash("a".repeat(64))).isEqualTo("a".repeat(64));
    }
}
