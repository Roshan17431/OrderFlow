package com.orderflow.auth.security.oauth2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oauth2")
public class VerifiedEmailOAuth2UserService extends DefaultOAuth2UserService {

    private final RestClient restClient = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {
        OAuth2User loaded = super.loadUser(userRequest);
        if (!"github".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return loaded;
        }
        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + userRequest.getAccessToken().getTokenValue())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return withVerifiedGitHubEmail(userRequest, loaded, emails);
    }

    OAuth2User withVerifiedGitHubEmail(
            OAuth2UserRequest userRequest,
            OAuth2User loaded,
            List<Map<String, Object>> emails) {
        Map<String, Object> verified = emails == null ? null : emails.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("primary"))
                        && Boolean.TRUE.equals(item.get("verified")))
                .findFirst()
                .orElse(null);
        if (verified == null || verified.get("email") == null) {
            throw new OAuth2AuthenticationException("verified_email_required");
        }
        Map<String, Object> attributes = new LinkedHashMap<>(loaded.getAttributes());
        attributes.put("email", verified.get("email"));
        attributes.put("email_verified", true);
        return new DefaultOAuth2User(
                loaded.getAuthorities(),
                attributes,
                userRequest.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName());
    }
}
