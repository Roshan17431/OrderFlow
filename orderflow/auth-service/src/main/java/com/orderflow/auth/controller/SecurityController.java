package com.orderflow.auth.controller;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    @GetMapping("/authenticated")
    public Map<String, Object> authenticated(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("authenticated", true, "userId", jwt.getSubject());
    }

    @GetMapping("/customer")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    public Map<String, String> customer() {
        return Map.of("scope", "customer");
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> admin() {
        return Map.of("scope", "admin");
    }
}
