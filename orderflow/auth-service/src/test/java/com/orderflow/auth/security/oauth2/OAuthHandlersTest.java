package com.orderflow.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.config.OAuth2Properties;
import com.orderflow.auth.domain.oauth.OAuthProvider;
import com.orderflow.auth.domain.user.User;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuthHandlersTest {

    @Test
    void githubVerifiedEmailResponseSelectsVerifiedPrimaryEmail() {
        VerifiedEmailOAuth2UserService service = new VerifiedEmailOAuth2UserService();
        OAuth2User loaded = githubUser(Map.of(
                "id", "42",
                "login", "octocat"));
        List<Map<String, Object>> providerResponse = List.of(
                Map.of(
                        "email", "secondary@example.com",
                        "primary", false,
                        "verified", true),
                Map.of(
                        "email", "primary@example.com",
                        "primary", true,
                        "verified", true));

        OAuth2User enriched = service.withVerifiedGitHubEmail(
                githubRequest(),
                loaded,
                providerResponse);

        assertThat(enriched.<String>getAttribute("email"))
                .isEqualTo("primary@example.com");
        assertThat(enriched.<Boolean>getAttribute("email_verified")).isTrue();
    }

    @Test
    void githubResponseWithoutVerifiedPrimaryEmailIsRejected() {
        VerifiedEmailOAuth2UserService service = new VerifiedEmailOAuth2UserService();

        assertThatThrownBy(() -> service.withVerifiedGitHubEmail(
                githubRequest(),
                githubUser(Map.of("id", "42", "login", "octocat")),
                List.of(Map.of(
                        "email", "unverified@example.com",
                        "primary", true,
                        "verified", false))))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void successRedirectContainsOnlyOneTimeCodeAndFailureRedirectContainsNoTokens()
            throws Exception {
        OAuthAccountService accounts = mock(OAuthAccountService.class);
        OAuth2Properties properties = new OAuth2Properties(
                "http://localhost:5173/oauth2/success",
                "http://localhost:5173/oauth2/failure");
        User user = User.oauthCustomer("person@example.com", "Person", Instant.now());
        when(accounts.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(accounts.issueExchangeCode(user.getId())).thenReturn("one-time-code");
        OAuth2SuccessHandler success = new OAuth2SuccessHandler(accounts, properties);
        MockHttpServletResponse successResponse = new MockHttpServletResponse();
        OAuth2User principal = githubUser(Map.of(
                "id", "42",
                "login", "person",
                "name", "Person",
                "email", "person@example.com",
                "email_verified", true));

        success.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                successResponse,
                new OAuth2AuthenticationToken(
                        principal,
                        principal.getAuthorities(),
                        "github"));

        assertThat(successResponse.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/oauth2/success?code=one-time-code")
                .doesNotContain("accessToken", "refreshToken", "Bearer", "eyJ");
        ArgumentCaptor<OAuthIdentity> identity = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(accounts).resolve(identity.capture());
        assertThat(identity.getValue().provider()).isEqualTo(OAuthProvider.GITHUB);
        assertThat(identity.getValue().emailVerified()).isTrue();

        OAuth2FailureHandler failure = new OAuth2FailureHandler(properties);
        MockHttpServletResponse failureResponse = new MockHttpServletResponse();
        failure.onAuthenticationFailure(
                new MockHttpServletRequest(),
                failureResponse,
                new OAuth2AuthenticationException("provider_failure"));
        assertThat(failureResponse.getRedirectedUrl())
                .isEqualTo("http://localhost:5173/oauth2/failure")
                .doesNotContain("token", "code");
    }

    private OAuth2UserRequest githubRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("test-client-id")
                .clientSecret("test-only-placeholder")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://provider.invalid/authorize")
                .tokenUri("https://provider.invalid/token")
                .userInfoUri("https://provider.invalid/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
        return new OAuth2UserRequest(
                registration,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "test-provider-token",
                        Instant.now(),
                        Instant.now().plusSeconds(60)));
    }

    private OAuth2User githubUser(Map<String, Object> attributes) {
        Map<String, Object> mutableAttributes = new LinkedHashMap<>(attributes);
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                mutableAttributes,
                "id");
    }
}
