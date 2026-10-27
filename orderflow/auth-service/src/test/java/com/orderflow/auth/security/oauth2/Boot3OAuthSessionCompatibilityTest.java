package com.orderflow.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class Boot3OAuthSessionCompatibilityTest {
    @Test
    void readsExistingOAuthHandshakeAndPreservesSessionFields() throws Exception {
        byte[] oldBytes;
        try (var input = getClass().getResourceAsStream(
                "/compat/boot3/oauth2-authorization-request.base64")) {
            oldBytes = Base64.getDecoder().decode(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        var serializer = new JdkSerializationRedisSerializer();
        var request = (OAuth2AuthorizationRequest) serializer.deserialize(oldBytes);
        assertThat(request.getState()).isEqualTo("synthetic-state");
        assertThat(request.getScopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(request.getRedirectUri()).isEqualTo(
                "https://orderflow.example.test/login/oauth2/code/synthetic");
        // Java collection iteration may change serialized byte order. Assert
        // every supported session field; the separate legacy-runtime check
        // verifies Security 6 reads the Security 7 fixture as well.
        var rewritten = (OAuth2AuthorizationRequest) serializer.deserialize(serializer.serialize(request));
        assertThat(rewritten.getState()).isEqualTo(request.getState());
        assertThat(rewritten.getAuthorizationRequestUri()).isEqualTo(request.getAuthorizationRequestUri());
        assertThat(rewritten.getClientId()).isEqualTo(request.getClientId());
        assertThat(rewritten.getAuthorizationUri()).isEqualTo(request.getAuthorizationUri());
        assertThat(rewritten.getScopes()).isEqualTo(request.getScopes());
        assertThat(rewritten.getAttributes()).isEqualTo(request.getAttributes());
        assertThat(rewritten.getAdditionalParameters()).isEqualTo(request.getAdditionalParameters());
        assertThat(java.io.ObjectStreamClass.lookup(OAuth2AuthorizationRequest.class).getSerialVersionUID()).isEqualTo(620L);
    }
}
