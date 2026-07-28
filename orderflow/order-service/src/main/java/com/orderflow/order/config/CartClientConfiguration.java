package com.orderflow.order.config;

import com.orderflow.order.client.CartClientInterceptor;
import com.orderflow.order.client.TemporaryCartServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CartClientConfiguration {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(
            name = "app.clients.cart.discovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    RestClient.Builder orderLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(name = "app.clients.cart.discovery-enabled", havingValue = "false")
    RestClient.Builder orderDirectRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient orderCartRestClient(
            RestClient.Builder builder,
            CartClientProperties properties,
            CartClientInterceptor interceptor) {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    CircuitBreakerRegistry orderCartCircuitBreakers(MeterRegistry meters) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
                .recordException(failure -> failure instanceof TemporaryCartServiceException)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    RetryRegistry orderCartRetries(CartClientProperties properties, MeterRegistry meters) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.retryWait())
                .retryOnException(failure -> failure instanceof TemporaryCartServiceException)
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meters);
        return registry;
    }
}
