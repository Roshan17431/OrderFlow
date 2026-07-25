package com.orderflow.auth.domain.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected User() {
    }

    public static User registeredCustomer(
            String email,
            String passwordHash,
            String displayName,
            Instant now) {
        return create(email, passwordHash, displayName, false, now);
    }

    public static User oauthCustomer(
            String email,
            String displayName,
            Instant now) {
        return create(email, null, displayName, true, now);
    }

    private static User create(
            String email,
            String passwordHash,
            String displayName,
            boolean emailVerified,
            Instant now) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.status = UserStatus.ACTIVE;
        user.emailVerified = emailVerified;
        user.tokenVersion = 0;
        user.createdAt = now;
        user.updatedAt = now;
        user.roles.add(Role.CUSTOMER);
        return user;
    }

    public void addRole(Role role) {
        roles.add(role);
        updatedAt = Instant.now();
    }

    public long incrementTokenVersion() {
        tokenVersion++;
        updatedAt = Instant.now();
        return tokenVersion;
    }

    public void lock() {
        status = UserStatus.LOCKED;
        updatedAt = Instant.now();
    }

    public void disable() {
        status = UserStatus.DISABLED;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
