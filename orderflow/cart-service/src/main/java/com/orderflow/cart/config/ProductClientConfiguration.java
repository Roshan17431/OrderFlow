package com.orderflow.cart.config;

import com.orderflow.cart.client.ProductClientInterceptor;
import com.orderflow.cart.client.ProductFailureClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @ConditionalOnProperty(
            name = "app.clients.product.discovery-enabled",
            havingValue = "false")
    RestClient.Builder directRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient productRestClient(
            RestClient.Builder builder,
            ProductClientProperties properties,
            ProductClientInterceptor interceptor) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    CircuitBreakerRegistry productCircuitBreakerRegistry(
            ProductResilienceProperties properties, MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.openStateDuration())
                .recordException(ProductFailureClassifier::isTemporary)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry)
                .bindTo(meterRegistry);
        return registry;
    }

    @Bean
    RetryRegistry productRetryRegistry(
            ProductResilienceProperties properties, MeterRegistry meterRegistry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .waitDuration(properties.retryWait())
                .retryOnException(ProductFailureClassifier::isTemporary)
                .build();
        RetryRegistry registry = RetryRegistry.of(config);
        TaggedRetryMetrics.ofRetryRegistry(registry).bindTo(meterRegistry);
        return registry;
    }
}
