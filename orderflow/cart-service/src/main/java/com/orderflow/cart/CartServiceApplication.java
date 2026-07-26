package com.orderflow.cart;

import com.orderflow.cart.config.CartProperties;
import com.orderflow.cart.config.CartSecurityProperties;
import com.orderflow.cart.config.ProductClientProperties;
import com.orderflow.cart.config.ProductResilienceProperties;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({
    CartProperties.class,
    CartSecurityProperties.class,
    ProductClientProperties.class,
    ProductResilienceProperties.class
})
public class CartServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
