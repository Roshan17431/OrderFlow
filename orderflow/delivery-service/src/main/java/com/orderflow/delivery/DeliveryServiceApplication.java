package com.orderflow.delivery;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DeliveryServiceApplication {
    public static void main(String[] args) { SpringApplication.run(DeliveryServiceApplication.class, args); }
    @Bean Clock deliveryClock() { return Clock.systemUTC(); }
}
