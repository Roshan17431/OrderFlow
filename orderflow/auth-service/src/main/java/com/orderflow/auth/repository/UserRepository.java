package com.orderflow.auth.repository;

import com.orderflow.auth.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<User> findById(UUID id);

    boolean existsByEmail(String email);
}
