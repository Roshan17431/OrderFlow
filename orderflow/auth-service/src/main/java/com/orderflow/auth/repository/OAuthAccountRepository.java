package com.orderflow.auth.repository;

import com.orderflow.auth.domain.oauth.OAuthAccount;
import com.orderflow.auth.domain.oauth.OAuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<OAuthAccount> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId);
}
