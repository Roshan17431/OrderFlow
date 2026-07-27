package com.orderflow.gateway.security;

import com.orderflow.gateway.config.GatewaySecurityProperties;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfiguration {

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(GatewaySecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new GatewayRequiredClaimsValidator()));
        return decoder;
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return List.of();
            }
            return roles.stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    @Bean
    SecurityWebFilterChain gatewaySecurityFilterChain(
            ServerHttpSecurity http,
            GatewayAuthenticationEntryPoint entryPoint,
            GatewayAccessDeniedHandler deniedHandler,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> authenticationConverter) {
        CookieServerCsrfTokenRepository csrf =
                CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        ServerCsrfTokenRequestAttributeHandler csrfHandler =
                new ServerCsrfTokenRequestAttributeHandler();
        csrfHandler.setTokenFromMultipartDataEnabled(false);

        return http
                .csrf(configurer -> configurer
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(csrfHandler))
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(authorize -> authorize
                        .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .pathMatchers(
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/api/platform/ping",
                                "/api/platform/info",
                                "/api/auth/csrf",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/oauth2/exchange",
                                "/api/auth/.well-known/jwks.json",
                                "/oauth2/**",
                                "/login/oauth2/**")
                        .permitAll()
                        .pathMatchers("/api/security/admin")
                        .hasRole("ADMIN")
                        .pathMatchers("/api/security/customer")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        .pathMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/api/products",
                                "/api/products/**",
                                "/api/categories",
                                "/api/categories/**")
                        .permitAll()
                        .pathMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/api/products/resolve")
                        .authenticated()
                        .pathMatchers("/api/vendor/products/**")
                        .hasAnyRole("VENDOR", "ADMIN")
                        .pathMatchers("/api/admin/categories/**", "/api/admin/products/**")
                        .hasRole("ADMIN")
                        .pathMatchers("/api/cart", "/api/cart/**")
                        .authenticated()
                        .anyExchange()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .build();
    }
}
