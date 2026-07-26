package com.orderflow.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderflow.auth.domain.oauth.OAuthAccount;
import com.orderflow.auth.domain.oauth.OAuthProvider;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.auth.repository.OAuthAccountRepository;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.EmailNormalizer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuthAccountServiceTest {

    @Test
    void verifiedGoogleIdentityCreatesCustomerAndProviderAccount() {
        OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-subject"))
                .thenReturn(Optional.empty());
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OAuthAccountService service = new OAuthAccountService(
                accounts,
                users,
                new EmailNormalizer(),
                mock(OAuthExchangeCodeStore.class));

        User created = service.resolve(new OAuthIdentity(
                OAuthProvider.GOOGLE,
                "google-subject",
                "NEW@example.com",
                true,
                "New User"));

        assertThat(created.getEmail()).isEqualTo("new@example.com");
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.getRoles()).containsExactly(com.orderflow.auth.domain.user.Role.CUSTOMER);
        ArgumentCaptor<OAuthAccount> persisted = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(accounts).save(persisted.capture());
        assertThat(persisted.getValue().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(persisted.getValue().getProviderUserId()).isEqualTo("google-subject");
    }

    @Test
    void verifiedMatchingEmailLinksWithoutReplacingPassword() {
        OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
        UserRepository users = mock(UserRepository.class);
        OAuthExchangeCodeStore codes = mock(OAuthExchangeCodeStore.class);
        User existing = User.registeredCustomer(
                "person@example.com",
                "existing-password-hash",
                "Person",
                Instant.now());
        when(accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "subject"))
                .thenReturn(Optional.empty());
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        OAuthAccountService service =
                new OAuthAccountService(accounts, users, new EmailNormalizer(), codes);

        User resolved = service.resolve(new OAuthIdentity(
                OAuthProvider.GOOGLE,
                "subject",
                "PERSON@example.com",
                true,
                "Person"));

        assertThat(resolved).isSameAs(existing);
        assertThat(resolved.getPasswordHash()).isEqualTo("existing-password-hash");
        ArgumentCaptor<OAuthAccount> linked = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(accounts).save(linked.capture());
        assertThat(linked.getValue().getProvider()).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    void unverifiedProviderEmailIsRejected() {
        OAuthAccountService service = new OAuthAccountService(
                mock(OAuthAccountRepository.class),
                mock(UserRepository.class),
                new EmailNormalizer(),
                mock(OAuthExchangeCodeStore.class));

        assertThatThrownBy(() -> service.resolve(new OAuthIdentity(
                OAuthProvider.GITHUB,
                "subject",
                "person@example.com",
                false,
                "Person")))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void existingProviderLinkReturnsItsUserWithoutRelinkingByEmail() {
        OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
        UserRepository users = mock(UserRepository.class);
        User linkedUser = User.oauthCustomer(
                "linked@example.com", "Linked", Instant.now());
        OAuthAccount linked = new OAuthAccount(
                linkedUser,
                OAuthProvider.GITHUB,
                "github-id",
                linkedUser.getEmail(),
                Instant.now());
        when(accounts.findByProviderAndProviderUserId(OAuthProvider.GITHUB, "github-id"))
                .thenReturn(Optional.of(linked));
        OAuthAccountService service = new OAuthAccountService(
                accounts,
                users,
                new EmailNormalizer(),
                mock(OAuthExchangeCodeStore.class));

        User resolved = service.resolve(new OAuthIdentity(
                OAuthProvider.GITHUB,
                "github-id",
                "different@example.com",
                true,
                "Different"));

        assertThat(resolved).isSameAs(linkedUser);
        verify(users, never()).findByEmail(any());
        verify(accounts, never()).save(any());
    }
}
