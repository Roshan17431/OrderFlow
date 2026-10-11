package com.orderflow.notification.service;

import org.springframework.stereotype.Component;

@Component
public class DestinationMasker {
    public String email(String value) {
        if (value == null || value.isBlank()) return null;
        int at = value.indexOf('@');
        return at <= 0 ? "***" : value.charAt(0) + "***" + value.substring(at);
    }

    public String phone(String value) {
        if (value == null || value.isBlank()) return null;
        return "******" + value.substring(Math.max(0, value.length() - 4));
    }
}
