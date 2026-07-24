package com.orderflow.auth.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.platform.http.CorrelationHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void putsValidatedIdInRequestResponseAndMdcThenClearsMdc()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationHeaders.CORRELATION_ID, "known-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observedMdc = new String[1];
        FilterChain chain = (servletRequest, servletResponse) ->
                observedMdc[0] = MDC.get(CorrelationIdFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(CorrelationRequestAttributes.CORRELATION_ID))
                .isEqualTo("known-id");
        assertThat(response.getHeader(CorrelationHeaders.CORRELATION_ID))
                .isEqualTo("known-id");
        assertThat(observedMdc[0]).isEqualTo("known-id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesAnUnsafeId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationHeaders.CORRELATION_ID, "bad value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationHeaders.CORRELATION_ID))
                .isNotBlank()
                .isNotEqualTo("bad value");
    }
}
