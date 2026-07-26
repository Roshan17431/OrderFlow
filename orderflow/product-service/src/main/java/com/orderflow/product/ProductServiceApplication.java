package com.orderflow.product;

import com.orderflow.product.config.CatalogCacheProperties;
import com.orderflow.product.config.CatalogProperties;
import com.orderflow.product.config.ProductSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties({
    CatalogProperties.class,
    CatalogCacheProperties.class,
    ProductSecurityProperties.class
})
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
