package com.orderflow.auth.config;

import com.orderflow.auth.security.RestAccessDeniedHandler;
import com.orderflow.auth.security.RestAuthenticationEntryPoint;
import com.orderflow.auth.security.oauth2.OAuth2FailureHandler;
import com.orderflow.auth.security.oauth2.OAuth2SuccessHandler;
import com.orderflow.auth.security.oauth2.VerifiedEmailOAuth2UserService;
import com.orderflow.auth.security.revocation.RevocationValidationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            RevocationValidationFilter revocationFilter,
            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
            CookieProperties cookies,
            ObjectProvider<OAuth2SuccessHandler> oauthSuccess,
            ObjectProvider<OAuth2FailureHandler> oauthFailure,
            ObjectProvider<VerifiedEmailOAuth2UserService> oauthUserService)
            throws Exception {
        CookieCsrfTokenRepository csrfRepository = csrfRepository(cookies);
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
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
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(revocationFilter, BearerTokenAuthenticationFilter.class);
        OAuth2SuccessHandler successHandler = oauthSuccess.getIfAvailable();
        if (successHandler != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(oauthUserService.getObject()))
                    .successHandler(successHandler)
                    .failureHandler(oauthFailure.getObject()));
        }
        return http.build();
    }

    static CookieCsrfTokenRepository csrfRepository(CookieProperties cookies) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(cookies.secure()).sameSite(cookies.sameSite()));
        return repository;
    }
}
