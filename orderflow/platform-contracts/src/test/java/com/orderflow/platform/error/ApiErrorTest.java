package com.orderflow.platform.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ApiErrorTest {

    @Test
    void normalizesNullFieldErrorsToAnImmutableEmptyList() {
        ApiError error = new ApiError(
                Instant.EPOCH, 500, "Internal Server Error", "INTERNAL_ERROR",
                "Unexpected error", "/", "test-id", null);

        assertThat(error.fieldErrors()).isEmpty();
        assertThat(error.fieldErrors()).isUnmodifiable();
    }
}
