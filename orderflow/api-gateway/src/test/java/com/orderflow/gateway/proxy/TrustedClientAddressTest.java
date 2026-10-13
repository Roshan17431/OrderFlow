package com.orderflow.gateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class TrustedClientAddressTest {
    @Test
    void untrustedPeerCannotChooseRateLimitIdentityWithForwardingHeaders() {
        var request = MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 12345))
                .header("X-Forwarded-For", "198.51.100.3")
                .header("Forwarded", "for=198.51.100.4;proto=https").build();
        assertThat(new TrustedClientAddress("172.31.250.2").resolve(request)).isEqualTo("192.0.2.10");
    }
    @Test
    void onlyConfiguredPhysicalProxyMayProvideSingleLiteralClientAddress() {
        var request = MockServerHttpRequest.get("/api/auth/login")
                .remoteAddress(new InetSocketAddress("172.31.250.2", 12345))
                .header("X-Forwarded-For", "198.51.100.3").build();
        assertThat(new TrustedClientAddress("172.31.250.2").resolve(request)).isEqualTo("198.51.100.3");
    }
    @Test
    void ambiguousChainsOrHostnamesFallBackToPhysicalPeer() {
        for (String header : new String[]{"198.51.100.3, 203.0.113.1", "attacker.example", "999.1.1.1", ""}) {
            var request = MockServerHttpRequest.get("/")
                    .remoteAddress(new InetSocketAddress("172.31.250.2", 12345))
                    .header("X-Forwarded-For", header).build();
            assertThat(new TrustedClientAddress("172.31.250.2").resolve(request)).isEqualTo("172.31.250.2");
        }
    }
    @Test
    void blankTrustConfigurationIgnoresAllForwardingHeaders() {
        var request = MockServerHttpRequest.get("/")
                .remoteAddress(new InetSocketAddress("172.31.250.2", 12345))
                .header("X-Forwarded-For", "198.51.100.3").build();
        assertThat(new TrustedClientAddress("").resolve(request)).isEqualTo("172.31.250.2");
    }
}
