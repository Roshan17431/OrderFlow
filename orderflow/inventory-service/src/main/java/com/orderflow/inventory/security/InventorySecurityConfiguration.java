package com.orderflow.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class InventorySecurityConfiguration {

    @Bean
    SecurityFilterChain inventorySecurityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler deniedHandler,
            RevocationValidationFilter revocationFilter,
            Converter<Jwt, AbstractAuthenticationToken> converter)
            throws Exception {
        return http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus")
                        .permitAll()
                        .requestMatchers("/api/inventory/reservations/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/inventory/**")
                        .hasAnyRole("VENDOR", "ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterAfter(revocationFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
