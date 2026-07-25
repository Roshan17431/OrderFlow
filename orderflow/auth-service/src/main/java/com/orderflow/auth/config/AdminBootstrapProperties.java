package com.orderflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.bootstrap.admin")
public record AdminBootstrapProperties(
        @DefaultValue("false") boolean enabled,
        String email,
        String password,
        String displayName) {
}
