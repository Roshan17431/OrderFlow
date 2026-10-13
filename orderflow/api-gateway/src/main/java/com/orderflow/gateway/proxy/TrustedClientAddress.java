package com.orderflow.gateway.proxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/** Resolve rate-limit identity only across the explicitly configured proxy boundary. */
@Component
public class TrustedClientAddress {
    private final String trustedAddress;

    public TrustedClientAddress(@Value("${app.proxy.trusted-address:}") String trustedAddress) {
        this.trustedAddress = trustedAddress.isBlank() ? "" : literal(trustedAddress);
        if (!trustedAddress.isBlank() && this.trustedAddress == null) {
            throw new IllegalArgumentException("Trusted proxy must be one literal IP address");
        }
    }

    public String resolve(ServerHttpRequest request) {
        var remote = request.getRemoteAddress();
        String peer = remote == null || remote.getAddress() == null
                ? "unknown" : remote.getAddress().getHostAddress();
        if (trustedAddress.isBlank() || !trustedAddress.equals(peer)) return peer;
        List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded == null || forwarded.size() != 1) return peer;
        String client = literal(forwarded.getFirst());
        return client == null ? peer : client;
    }

    private static String literal(String value) {
        if (value == null || value.length() > 45 || value.contains(",")) return null;
        String address = value.trim();
        if (address.contains(":")) {
            if (!address.matches("[0-9a-fA-F:]+")) return null;
            try {
                return InetAddress.getByName(address).getHostAddress();
            } catch (UnknownHostException exception) {
                return null;
            }
        }
        if (!address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
        String[] parts = address.split("\\.");
        for (String part : parts) if (Integer.parseInt(part) > 255) return null;
        return Integer.parseInt(parts[0]) + "." + Integer.parseInt(parts[1]) + "."
                + Integer.parseInt(parts[2]) + "." + Integer.parseInt(parts[3]);
    }
}
