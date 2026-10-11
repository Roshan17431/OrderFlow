package com.orderflow.payment.config;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SimulatorSafetyConfiguration {

    public SimulatorSafetyConfiguration(PaymentProperties properties, Environment environment) {
        if (!"simulator".equalsIgnoreCase(properties.provider())) {
            throw new IllegalStateException("Phase 5 supports only the simulator provider");
        }
        if (!properties.simulatorEnabled()) {
            throw new IllegalStateException("The selected simulator provider is disabled");
        }
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && !properties.simulatorAllowInProduction()) {
            throw new IllegalStateException(
                    "The payment simulator is prohibited in the production profile");
        }
    }
}
