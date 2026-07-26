package com.orderflow.auth.security;

import com.orderflow.auth.error.AuthenticationException;
import com.orderflow.platform.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    public void validate(String password, String normalizedEmail) {
        if (password == null
                || password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH
                || password.isBlank()
                || password.chars().allMatch(Character::isWhitespace)
                || password.equalsIgnoreCase(normalizedEmail)) {
            throw new AuthenticationException(
                    ErrorCode.VALIDATION_ERROR,
                    "Password must be between 12 and 128 characters and must not match the email.");
        }
    }
}
