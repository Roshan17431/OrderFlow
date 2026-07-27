package com.orderflow.cart;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.cart.security.RevocationStateService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=1",
    "spring.data.redis.connect-timeout=100ms",
    "spring.data.redis.timeout=100ms",
    "app.clients.product.discovery-enabled=false",
    "app.clients.product.base-url=http://localhost:1"
})
@AutoConfigureMockMvc
class CartStateUnavailableIT {

    private static final UUID USER_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RevocationStateService revocations;

    @BeforeEach
    void securityStateIsAvailable() {
        when(revocations.validate(anyString(), any(UUID.class), anyLong()))
                .thenReturn(RevocationStateService.Validation.VALID);
    }

    @Test
    void redisFailureReturnsSafeCartStateError() throws Exception {
        mockMvc.perform(get("/api/cart").with(jwt().jwt(builder -> builder
                                .subject(USER_ID.toString())
                                .jti(UUID.randomUUID().toString())
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(600))
                                .claim("email", "customer@example.com")
                                .claim("session_id", UUID.randomUUID().toString())
                                .claim("token_version", 0L)
                                .claim("roles", List.of("CUSTOMER")))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CART_STATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Cart state is temporarily unavailable."));
    }
}
