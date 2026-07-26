package com.orderflow.auth.error;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account with this email is already registered.");
    }
}
