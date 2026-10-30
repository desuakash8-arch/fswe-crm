package com.klu.crmbackend.config;

import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.UserRepository;
import com.klu.crmbackend.service.Rules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** First-run setup only. No public registration endpoint or default password. */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String name;
    private final String email;
    private final String password;

    public BootstrapAdmin(UserRepository users, PasswordEncoder passwords,
            @Value("${app.bootstrap.name:CRM Administrator}") String name,
            @Value("${app.bootstrap.email:}") String email,
            @Value("${app.bootstrap.password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() != 0) {
            log.info("Bootstrap skipped: accounts already exist; no account was changed.");
            return;
        }
        String address = Rules.email(email);
        if (address == null) throw new IllegalArgumentException("BOOTSTRAP_ADMIN_EMAIL is required for first-run setup.");
        Rules.password(password);
        var user = new User();
        user.setName(Rules.text(name, "Administrator name", true, 255));
        user.setEmail(address);
        user.setPassword(passwords.encode(password));
        user.setRole("ADMIN");
        user.setActive(true);
        users.save(user);
        log.info("First administrator created. Disable BOOTSTRAP_ADMIN_ENABLED and remove its password from the environment.");
    }
}
