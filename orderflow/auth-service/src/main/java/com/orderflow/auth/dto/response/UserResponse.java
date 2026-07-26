package com.orderflow.auth.dto.response;

import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.domain.user.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserStatus status,
        List<String> roles,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.getCreatedAt());
    }
}
