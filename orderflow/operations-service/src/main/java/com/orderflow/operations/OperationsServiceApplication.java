package com.orderflow.operations;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableMethodSecurity
public class OperationsServiceApplication {
    public static void main(String[] args) { SpringApplication.run(OperationsServiceApplication.class, args); }
    @Bean Clock operationsClock() { return Clock.systemUTC(); }
}
