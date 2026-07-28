package com.orderflow.inventory.config;

import com.orderflow.inventory.client.ProductClientInterceptor;
import com.orderflow.inventory.client.TemporaryProductServiceException;
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
public class ProductClientConfiguration {

    @Bean
    @LoadBalanced
    @ConditionalOnProperty(
            name = "app.clients.product.discovery-enabled",
            havingValue = "true",
            matchIfMissing = true)
    RestClient.Builder inventoryLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.clients.product.discovery-enabled",
            havingValue = "false")
    RestClient.Builder inventoryDirectRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient inventoryProductRestClient(
            RestClient.Builder builder,
            ProductClientProperties properties,
            ProductClientInterceptor interceptor) {
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
    CircuitBreakerRegistry inventoryProductCircuitBreakers(MeterRegistry meters) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(java.time.Duration.ofSeconds(30))
                .recordException(failure -> failure instanceof TemporaryProductServiceException)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    @Bean
    RetryRegistry inventoryProductRetries(
            ProductClientProperties properties, MeterRegistry meters) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.retryWait())
                .retryOnException(failure -> failure instanceof TemporaryProductServiceException)
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meters);
        return registry;
    }
}
