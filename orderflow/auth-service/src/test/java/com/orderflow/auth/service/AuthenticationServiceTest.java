package com.orderflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.audit.SecurityAuditService;
import com.orderflow.auth.domain.user.Role;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.dto.request.LoginRequest;
import com.orderflow.auth.dto.request.RegisterRequest;
import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.EmailNormalizer;
import com.orderflow.auth.security.PasswordPolicy;
import com.orderflow.auth.security.PasswordSecurity;
import com.orderflow.auth.security.jwt.IssuedAccessToken;
import com.orderflow.auth.security.jwt.JwtService;
import com.orderflow.auth.security.rate.LoginAttemptService;
import com.orderflow.auth.security.refresh.IssuedRefreshToken;
import com.orderflow.auth.security.refresh.RefreshTokenMetadata;
import com.orderflow.auth.security.refresh.RefreshTokenStore;
import com.orderflow.auth.security.revocation.RevocationStateService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthenticationServiceTest {

    private UserRepository users;
    private PasswordSecurity passwords;
    private RefreshTokenStore refreshTokens;
    private LoginAttemptService attempts;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordSecurity.class);
        JwtService jwt = mock(JwtService.class);
        refreshTokens = mock(RefreshTokenStore.class);
        RevocationStateService revocations = mock(RevocationStateService.class);
        attempts = mock(LoginAttemptService.class);
        SecurityAuditService audit = mock(SecurityAuditService.class);
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.create(any(), anyLong(), any(), any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            long version = invocation.getArgument(1);
            UUID session = UUID.randomUUID();
            Instant now = Instant.now();
            return new IssuedRefreshToken(
                    "raw-refresh",
                    new RefreshTokenMetadata(
                            session,
                            UUID.randomUUID(),
                            userId,
                            "a".repeat(64),
                            now,
                            now.plusSeconds(300),
                            false,
                            false,
                            "",
                            "b".repeat(64),
                            "c".repeat(64),
                            version));
        });
        when(jwt.issue(any(User.class), any(UUID.class))).thenReturn(
                new IssuedAccessToken("access", UUID.randomUUID().toString(), Instant.now()));
        service = new AuthenticationService(
                users,
                new EmailNormalizer(),
                new PasswordPolicy(),
                passwords,
                jwt,
                refreshTokens,
                revocations,
                attempts,
                audit);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndAssignsCustomerOnly() {
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(passwords.hash("very strong password")).thenReturn("bcrypt-hash");

        AuthenticationResult result = service.register(
                new RegisterRequest(
                        " Person@Example.COM ",
                        "very strong password",
                        " Person "),
                "a".repeat(64),
                "b".repeat(64),
                "correlation");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("person@example.com");
        assertThat(saved.getValue().getPasswordHash())
                .isEqualTo("bcrypt-hash")
                .isNotEqualTo("very strong password");
        assertThat(saved.getValue().getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(result.accessToken().value()).isEqualTo("access");
    }

    @Test
    void unknownEmailAndWrongPasswordUseTheSameGenericError() {
        when(users.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        User existing = User.registeredCustomer(
                "existing@example.com", "hash", "Existing", Instant.now());
        when(users.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(passwords.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.login(
                new LoginRequest("unknown@example.com", "incorrect password"),
                "a".repeat(64),
                "b".repeat(64),
                "correlation"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Email or password is incorrect.");
        verify(attempts).recordFailure("unknown@example.com", "a".repeat(64));

        assertThatThrownBy(() -> service.login(
                new LoginRequest("existing@example.com", "incorrect password"),
                "a".repeat(64),
                "b".repeat(64),
                "correlation"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Email or password is incorrect.");
        verify(attempts).recordFailure("existing@example.com", "a".repeat(64));
    }

    @Test
    void lockedAndDisabledAccountsCannotLogin() {
        User locked = User.registeredCustomer(
                "locked@example.com", "hash", "Locked", Instant.now());
        locked.lock();
        when(users.findByEmail("locked@example.com")).thenReturn(Optional.of(locked));
        when(passwords.matches("correct password", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(
                new LoginRequest("locked@example.com", "correct password"),
                "a".repeat(64),
                "b".repeat(64),
                "correlation"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("This account cannot sign in.");

        User disabled = User.registeredCustomer(
                "disabled@example.com", "hash", "Disabled", Instant.now());
        disabled.disable();
        when(users.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.login(
                new LoginRequest("disabled@example.com", "correct password"),
                "a".repeat(64),
                "b".repeat(64),
                "correlation"))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("This account cannot sign in.");
    }

    @Test
    void successfulLoginClearsTemporaryFailureState() {
        User active = User.registeredCustomer(
                "active@example.com", "hash", "Active", Instant.now());
        when(users.findByEmail("active@example.com")).thenReturn(Optional.of(active));
        when(passwords.matches("correct password", "hash")).thenReturn(true);

        AuthenticationResult result = service.login(
                new LoginRequest("ACTIVE@example.com", "correct password"),
                "a".repeat(64),
                "b".repeat(64),
                "correlation");

        verify(attempts).clear("active@example.com", "a".repeat(64));
        assertThat(result.accessToken().value()).isEqualTo("access");
    }
}
