package com.klu.crmbackend;

import com.klu.crmbackend.config.BootstrapAdmin;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BootstrapAdminTests {
    @Test void createsFirstAdminWithHashedPassword() {
        var users = mock(UserRepository.class);
        var passwords = new BCryptPasswordEncoder();
        var bootstrap = new BootstrapAdmin(users, passwords, "First Admin", " ADMIN@test.invalid ", "Test-only-password-123");
        bootstrap.run(new DefaultApplicationArguments());
        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertEquals("admin@test.invalid", saved.getValue().getEmail());
        assertTrue(passwords.matches("Test-only-password-123", saved.getValue().getPassword()));
        assertTrue(saved.getValue().getActive());
    }

    @Test void restartNeverCreatesOrResetsAnExistingAccount() {
        var users = mock(UserRepository.class);
        when(users.count()).thenReturn(1L);
        new BootstrapAdmin(users, new BCryptPasswordEncoder(), "Admin", "", "").run(new DefaultApplicationArguments());
        verify(users).count();
        verifyNoMoreInteractions(users);
    }

    @Test void refusesMissingEmailAndWeakPasswordOnEmptyDatabase() {
        var users = mock(UserRepository.class);
        var passwords = new BCryptPasswordEncoder();
        assertThrows(IllegalArgumentException.class, () -> new BootstrapAdmin(users, passwords, "Admin", "", "Test-password-123").run(new DefaultApplicationArguments()));
        assertThrows(IllegalArgumentException.class, () -> new BootstrapAdmin(users, passwords, "Admin", "admin@test.invalid", "short").run(new DefaultApplicationArguments()));
        verify(users, never()).save(any(User.class));
    }
}
