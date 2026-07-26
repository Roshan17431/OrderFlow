package com.orderflow.auth.service;

import com.orderflow.auth.audit.SecurityAuditEvent;
import com.orderflow.auth.audit.SecurityAuditService;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.domain.user.UserStatus;
import com.orderflow.auth.dto.request.LoginRequest;
import com.orderflow.auth.dto.request.RegisterRequest;
import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.auth.error.DuplicateEmailException;
import com.orderflow.auth.error.LoginThrottledException;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.EmailNormalizer;
import com.orderflow.auth.security.PasswordPolicy;
import com.orderflow.auth.security.PasswordSecurity;
import com.orderflow.auth.security.jwt.IssuedAccessToken;
import com.orderflow.auth.security.jwt.JwtService;
import com.orderflow.auth.security.rate.LoginAttemptService;
import com.orderflow.auth.security.refresh.IssuedRefreshToken;
import com.orderflow.auth.security.refresh.RefreshRotationResult;
import com.orderflow.auth.security.refresh.RefreshTokenMetadata;
import com.orderflow.auth.security.refresh.RefreshTokenStore;
import com.orderflow.auth.security.revocation.RevocationStateService;
import com.orderflow.platform.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String DUMMY_BCRYPT =
            "$2a$12$q2A.EJUWit2KEGpUUypOSeWvhKYG1sjQSas5d2RZJYfUUE9ce2M5y";
    private final UserRepository users;
    private final EmailNormalizer emails;
    private final PasswordPolicy passwordPolicy;
    private final PasswordSecurity passwords;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokens;
    private final RevocationStateService revocations;
    private final LoginAttemptService attempts;
    private final SecurityAuditService audit;

    public AuthenticationService(
            UserRepository users,
            EmailNormalizer emails,
            PasswordPolicy passwordPolicy,
            PasswordSecurity passwords,
            JwtService jwtService,
            RefreshTokenStore refreshTokens,
            RevocationStateService revocations,
            LoginAttemptService attempts,
            SecurityAuditService audit) {
        this.users = users;
        this.emails = emails;
        this.passwordPolicy = passwordPolicy;
        this.passwords = passwords;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.attempts = attempts;
        this.audit = audit;
    }

    @Transactional
    public AuthenticationResult register(
            RegisterRequest request,
            String ipHash,
            String userAgentHash,
            String correlationId) {
        String email = emails.normalize(request.email());
        passwordPolicy.validate(request.password(), email);
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        User user = User.registeredCustomer(
                email,
                passwords.hash(request.password()),
                request.displayName().strip(),
                Instant.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
        AuthenticationResult result = createSession(user, ipHash, userAgentHash);
        audit.record(event(
                SecurityAuditEvent.Event.USER_REGISTERED,
                user,
                result.refreshToken().metadata().sessionId(),
                correlationId,
                ipHash,
                userAgentHash,
                null));
        return result;
    }

    @Transactional(readOnly = true)
    public AuthenticationResult login(
            LoginRequest request,
            String ipHash,
            String userAgentHash,
            String correlationId) {
        String email = emails.normalize(request.email());
        try {
            attempts.assertAllowed(email, ipHash);
        } catch (LoginThrottledException exception) {
            audit.record(event(
                    SecurityAuditEvent.Event.LOGIN_THROTTLED,
                    null,
                    null,
                    correlationId,
                    ipHash,
                    userAgentHash,
                    "ATTEMPT_LIMIT"));
            throw exception;
        }
        Optional<User> candidate = users.findByEmail(email);
        String hash = candidate.map(User::getPasswordHash).orElse(DUMMY_BCRYPT);
        boolean matches = passwords.matches(request.password(), hash);
        if (candidate.isEmpty() || !matches || candidate.get().getPasswordHash() == null) {
            recordFailedLogin(email, ipHash, userAgentHash, correlationId);
            throw invalidCredentials();
        }
        User user = candidate.get();
        assertLoginAllowed(user);
        attempts.clear(email, ipHash);
        AuthenticationResult result = createSession(user, ipHash, userAgentHash);
        audit.record(event(
                SecurityAuditEvent.Event.LOGIN_SUCCEEDED,
                user,
                result.refreshToken().metadata().sessionId(),
                correlationId,
                ipHash,
                userAgentHash,
                null));
        return result;
    }

    @Transactional(readOnly = true)
    public AuthenticationResult refresh(
            String rawToken,
            String ipHash,
            String userAgentHash,
            String correlationId) {
        RefreshRotationResult rotation = refreshTokens.rotate(rawToken, userAgentHash, ipHash);
        if (rotation.status() == RefreshRotationResult.Status.REUSE_DETECTED) {
            audit.record(event(
                    SecurityAuditEvent.Event.REFRESH_TOKEN_REUSE_DETECTED,
                    null,
                    null,
                    correlationId,
                    ipHash,
                    userAgentHash,
                    "ROTATED_TOKEN_REUSED"));
            throw new AuthenticationException(
                    ErrorCode.REFRESH_TOKEN_INVALID,
                    "The refresh token is invalid.");
        }
        if (rotation.status() != RefreshRotationResult.Status.ROTATED) {
            throw new AuthenticationException(
                    ErrorCode.REFRESH_TOKEN_INVALID,
                    "The refresh token is invalid.");
        }
        IssuedRefreshToken replacement = rotation.replacement();
        RefreshTokenMetadata metadata = replacement.metadata();
        User user = users.findById(metadata.userId())
                .orElseThrow(this::invalidRefresh);
        if (user.getStatus() != UserStatus.ACTIVE
                || user.getTokenVersion() != metadata.tokenVersion()) {
            refreshTokens.revokeFamily(metadata.familyId());
            throw invalidRefresh();
        }
        revocations.publishTokenVersion(user.getId(), user.getTokenVersion());
        IssuedAccessToken access = jwtService.issue(user, metadata.sessionId());
        audit.record(event(
                SecurityAuditEvent.Event.TOKEN_REFRESHED,
                user,
                metadata.sessionId(),
                correlationId,
                ipHash,
                userAgentHash,
                null));
        return new AuthenticationResult(user, access, replacement);
    }

    public void logout(
            Jwt jwt,
            String rawRefreshToken,
            String correlationId,
            String ipHash,
            String userAgentHash) {
        refreshTokens.revoke(rawRefreshToken);
        revocations.revokeJti(jwt.getId(), jwt.getExpiresAt());
        audit.record(event(
                SecurityAuditEvent.Event.LOGOUT_SUCCEEDED,
                null,
                sessionId(jwt),
                correlationId,
                ipHash,
                userAgentHash,
                null));
    }

    @Transactional
    public void logoutAll(
            Jwt jwt,
            String correlationId,
            String ipHash,
            String userAgentHash) {
        UUID userId = UUID.fromString(jwt.getSubject());
        User user = users.findById(userId)
                .orElseThrow(() -> new AuthenticationException(
                        ErrorCode.INVALID_TOKEN,
                        "The access token is invalid."));
        long latestVersion = user.incrementTokenVersion();
        users.saveAndFlush(user);
        refreshTokens.revokeAllForUser(userId);
        revocations.publishTokenVersion(userId, latestVersion);
        revocations.revokeJti(jwt.getId(), jwt.getExpiresAt());
        audit.record(event(
                SecurityAuditEvent.Event.LOGOUT_ALL_SUCCEEDED,
                user,
                sessionId(jwt),
                correlationId,
                ipHash,
                userAgentHash,
                null));
    }

    @Transactional(readOnly = true)
    public User currentUser(String subject) {
        try {
            return users.findById(UUID.fromString(subject))
                    .orElseThrow(() -> new AuthenticationException(
                            ErrorCode.INVALID_TOKEN,
                            "The access token is invalid."));
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException(
                    ErrorCode.INVALID_TOKEN,
                    "The access token is invalid.");
        }
    }

    @Transactional(readOnly = true)
    public AuthenticationResult oauthLogin(
            User user,
            String ipHash,
            String userAgentHash,
            String correlationId,
            String provider) {
        AuthenticationResult result = createSession(user, ipHash, userAgentHash);
        audit.record(new SecurityAuditEvent(
                SecurityAuditEvent.Event.OAUTH_LOGIN_SUCCEEDED,
                Instant.now(),
                user.getId(),
                result.refreshToken().metadata().sessionId(),
                provider,
                correlationId,
                ipHash,
                userAgentHash,
                null));
        return result;
    }

    private AuthenticationResult createSession(
            User user,
            String ipHash,
            String userAgentHash) {
        revocations.publishTokenVersion(user.getId(), user.getTokenVersion());
        IssuedRefreshToken refresh = refreshTokens.create(
                user.getId(),
                user.getTokenVersion(),
                userAgentHash,
                ipHash);
        IssuedAccessToken access = jwtService.issue(user, refresh.metadata().sessionId());
        return new AuthenticationResult(user, access, refresh);
    }

    private void recordFailedLogin(
            String email,
            String ipHash,
            String userAgentHash,
            String correlationId) {
        try {
            attempts.recordFailure(email, ipHash);
        } catch (LoginThrottledException exception) {
            audit.record(event(
                    SecurityAuditEvent.Event.LOGIN_THROTTLED,
                    null,
                    null,
                    correlationId,
                    ipHash,
                    userAgentHash,
                    "ATTEMPT_LIMIT"));
            throw exception;
        }
        audit.record(event(
                SecurityAuditEvent.Event.LOGIN_FAILED,
                null,
                null,
                correlationId,
                ipHash,
                userAgentHash,
                "INVALID_CREDENTIALS"));
    }

    private void assertLoginAllowed(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AuthenticationException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "This account cannot sign in.");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AuthenticationException(
                    ErrorCode.ACCOUNT_LOCKED,
                    "This account cannot sign in.");
        }
    }

    private AuthenticationException invalidCredentials() {
        return new AuthenticationException(
                ErrorCode.INVALID_CREDENTIALS,
                "Email or password is incorrect.");
    }

    private AuthenticationException invalidRefresh() {
        return new AuthenticationException(
                ErrorCode.REFRESH_TOKEN_INVALID,
                "The refresh token is invalid.");
    }

    private UUID sessionId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getClaimAsString("session_id"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private SecurityAuditEvent event(
            SecurityAuditEvent.Event type,
            User user,
            UUID sessionId,
            String correlationId,
            String ipHash,
            String userAgentHash,
            String reason) {
        return new SecurityAuditEvent(
                type,
                Instant.now(),
                user == null ? null : user.getId(),
                sessionId,
                null,
                correlationId,
                ipHash,
                userAgentHash,
                reason);
    }
}
