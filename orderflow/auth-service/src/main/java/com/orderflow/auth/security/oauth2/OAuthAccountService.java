package com.orderflow.auth.security.oauth2;

import com.orderflow.auth.domain.oauth.OAuthAccount;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.domain.user.UserStatus;
import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.auth.repository.OAuthAccountRepository;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.EmailNormalizer;
import com.orderflow.platform.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

    private final OAuthAccountRepository accounts;
    private final UserRepository users;
    private final EmailNormalizer emails;
    private final OAuthExchangeCodeStore codes;

    public OAuthAccountService(
            OAuthAccountRepository accounts,
            UserRepository users,
            EmailNormalizer emails,
            OAuthExchangeCodeStore codes) {
        this.accounts = accounts;
        this.users = users;
        this.emails = emails;
        this.codes = codes;
    }

    @Transactional
    public User resolve(OAuthIdentity identity) {
        if (!identity.emailVerified()
                || identity.providerUserId() == null
                || identity.providerUserId().isBlank()) {
            throw oauthFailure();
        }
        return accounts.findByProviderAndProviderUserId(
                        identity.provider(),
                        identity.providerUserId())
                .map(OAuthAccount::getUser)
                .orElseGet(() -> linkOrCreate(identity));
    }

    @Transactional(readOnly = true)
    public String issueExchangeCode(UUID userId) {
        User user = users.findById(userId).orElseThrow(this::oauthFailure);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthFailure();
        }
        return codes.issue(userId);
    }

    @Transactional(readOnly = true)
    public User consumeExchangeCode(String rawCode) {
        UUID userId = codes.consume(rawCode).orElseThrow(this::oauthFailure);
        User user = users.findById(userId).orElseThrow(this::oauthFailure);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthFailure();
        }
        return user;
    }

    private User linkOrCreate(OAuthIdentity identity) {
        String email = emails.normalize(identity.email());
        if (email == null || email.isBlank()) {
            throw oauthFailure();
        }
        User user = users.findByEmail(email).orElseGet(() -> users.save(
                User.oauthCustomer(
                        email,
                        safeDisplayName(identity.displayName(), email),
                        Instant.now())));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthFailure();
        }
        accounts.save(new OAuthAccount(
                user,
                identity.provider(),
                identity.providerUserId(),
                email,
                Instant.now()));
        return user;
    }

    private String safeDisplayName(String displayName, String email) {
        if (displayName == null || displayName.isBlank()) {
            return email.substring(0, email.indexOf('@'));
        }
        return displayName.strip().substring(0, Math.min(displayName.strip().length(), 100));
    }

    private AuthenticationException oauthFailure() {
        return new AuthenticationException(
                ErrorCode.OAUTH2_AUTHENTICATION_FAILED,
                "OAuth authentication could not be completed.");
    }
}
