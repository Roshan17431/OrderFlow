package com.orderflow.auth.config;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.oauth2")
public record OAuth2Properties(
        @NotBlank @DefaultValue("http://localhost:5173/oauth2/success")
                String successRedirectUri,
        @NotBlank @DefaultValue("http://localhost:5173/oauth2/failure")
                String failureRedirectUri) {

    public URI successRedirect() {
        return URI.create(successRedirectUri);
    }

    public URI failureRedirect() {
        return URI.create(failureRedirectUri);
    }
}
