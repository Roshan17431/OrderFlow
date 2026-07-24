package com.orderflow.auth.config;

import com.orderflow.auth.domain.user.Role;
import com.orderflow.auth.domain.user.User;
import com.orderflow.auth.repository.UserRepository;
import com.orderflow.auth.security.EmailNormalizer;
import com.orderflow.auth.security.PasswordPolicy;
import com.orderflow.auth.security.PasswordSecurity;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final EmailNormalizer emails;
    private final PasswordPolicy passwordPolicy;
    private final PasswordSecurity passwords;

    public AdminBootstrap(
            AdminBootstrapProperties properties,
            UserRepository users,
            EmailNormalizer emails,
            PasswordPolicy passwordPolicy,
            PasswordSecurity passwords) {
        this.properties = properties;
        this.users = users;
        this.emails = emails;
        this.passwordPolicy = passwordPolicy;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.email() == null
                || properties.password() == null
                || properties.displayName() == null
                || properties.displayName().isBlank()) {
            throw new IllegalStateException(
                    "All admin bootstrap settings are required when bootstrap is enabled");
        }
        String email = emails.normalize(properties.email());
        passwordPolicy.validate(properties.password(), email);
        if (users.existsByEmail(email)) {
            return;
        }
        User admin = User.registeredCustomer(
                email,
                passwords.hash(properties.password()),
                properties.displayName().strip(),
                Instant.now());
        admin.addRole(Role.ADMIN);
        users.saveAndFlush(admin);
        LOGGER.atInfo()
                .addKeyValue("event", "admin.bootstrap.completed")
                .addKeyValue("userId", admin.getId())
                .log("Local administrator bootstrap completed");
    }
}
