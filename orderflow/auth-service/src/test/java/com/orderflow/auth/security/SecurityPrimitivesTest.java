package com.orderflow.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.auth.config.CookieProperties;
import com.orderflow.auth.config.RefreshTokenProperties;
import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.auth.security.refresh.RefreshCookieService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SecurityPrimitivesTest {

    private final EmailNormalizer emails = new EmailNormalizer();
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void emailNormalizationStripsNormalizesAndUsesLocaleIndependentLowercase() {
        assertThat(emails.normalize("  UsEr@Example.COM  ")).isEqualTo("user@example.com");
        assertThat(emails.normalize("ＴＥＳＴ@example.com")).isEqualTo("test@example.com");
    }

    @Test
    void passwordPolicyPreservesWhitespaceAndAcceptsUnicode() {
        policy.validate("  strong 🔐 password  ", "person@example.com");
    }

    @Test
    void passwordPolicyRejectsShortWhitespaceOnlyAndEmailPasswords() {
        assertThatThrownBy(() -> policy.validate("short", "person@example.com"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> policy.validate("              ", "person@example.com"))
                .isInstanceOf(AuthenticationException.class);
        assertThatThrownBy(() -> policy.validate(
                "PERSON@EXAMPLE.COM",
                "person@example.com"))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void bcryptUsesDifferentSaltsAndNeverReturnsRawPassword() {
        PasswordSecurity passwords = new PasswordSecurity();
        String raw = "correct horse battery staple";
        String first = passwords.hash(raw);
        String second = passwords.hash(raw);

        assertThat(first).startsWith("$2").isNotEqualTo(raw).isNotEqualTo(second);
        assertThat(passwords.matches(raw, first)).isTrue();
        assertThat(passwords.matches("wrong password", first)).isFalse();
    }

    @Test
    void tokenHasherIsDeterministicAndDoesNotReturnRawValue() {
        TokenHasher hasher = new TokenHasher();
        String hash = hasher.sha256("opaque-token");
        assertThat(hash).hasSize(64).matches("[a-f0-9]+").isNotEqualTo("opaque-token");
        assertThat(hasher.sha256("opaque-token")).isEqualTo(hash);
    }

    @Test
    void refreshCookieIsHttpOnlyAndClearCookieExpiresImmediately() {
        RefreshCookieService cookies = new RefreshCookieService(
                new CookieProperties("ORDERFLOW_REFRESH", false, "Strict", "/api/auth"),
                new RefreshTokenProperties(Duration.ofDays(14), 32, Duration.ofSeconds(60)));

        assertThat(cookies.create("opaque").toString())
                .contains("ORDERFLOW_REFRESH=opaque")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .doesNotContain("Secure");
        assertThat(cookies.clear().getMaxAge()).isZero();
    }
}
