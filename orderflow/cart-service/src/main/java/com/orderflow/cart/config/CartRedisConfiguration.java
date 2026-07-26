package com.orderflow.cart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class CartRedisConfiguration {

    @Bean
    DefaultRedisScript<Long> addCartItemScript() {
        return script("redis/add-item.lua");
    }

    @Bean
    DefaultRedisScript<Long> updateCartItemScript() {
        return script("redis/update-item.lua");
    }

    @Bean
    DefaultRedisScript<Long> removeCartItemScript() {
        return script("redis/remove-item.lua");
    }

    @Bean
    DefaultRedisScript<Long> replaceCartItemsScript() {
        return script("redis/replace-items.lua");
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
