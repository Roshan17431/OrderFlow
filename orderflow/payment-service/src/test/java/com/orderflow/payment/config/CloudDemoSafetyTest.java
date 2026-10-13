package com.orderflow.payment.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CloudDemoSafetyTest {
    @Test
    void productionProfileCannotEnableSimulatorEvenWithLegacyOverride() {
        for (String profile : new String[]{"prod", "production"}) {
            var environment = new MockEnvironment().withProperty("app.payment.demo-completion-enabled", "true");
            environment.setActiveProfiles("cloud-demo", profile);
            var properties = new PaymentProperties("simulator", true, true, "unit-test-only-webhook-secret", Duration.ofMinutes(5), Duration.ofSeconds(2), Duration.ofSeconds(5), 2, Duration.ofMillis(250), 100);
            assertThatThrownBy(() -> new SimulatorSafetyConfiguration(properties, environment)).isInstanceOf(IllegalStateException.class);
        }
    }
}
