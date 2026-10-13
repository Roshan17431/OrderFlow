package com.orderflow.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class CloudCsrfCookieTest {
    @Test
    void configuredSecureOriginMakesCsrfCookieSecureEvenBehindInternalHttpProxy() {
        var response = new MockHttpServletResponse();
        var cookies = new CookieProperties("ORDERFLOW_REFRESH", true, "Strict", "/api/auth");
        SecurityConfiguration.csrfRepository(cookies).saveToken(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-only-csrf"),
                new MockHttpServletRequest(), response);
        assertThat(response.getHeader("Set-Cookie")).contains("Secure", "Path=/").doesNotContain("HttpOnly");
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Strict");
    }
    @Test
    void localHttpDevelopmentRetainsExplicitInsecureCookieConfiguration() {
        var response = new MockHttpServletResponse();
        SecurityConfiguration.csrfRepository(new CookieProperties("ORDERFLOW_REFRESH", false, "Strict", "/api/auth"))
                .saveToken(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-only-csrf"), new MockHttpServletRequest(), response);
        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Strict");
    }
}
