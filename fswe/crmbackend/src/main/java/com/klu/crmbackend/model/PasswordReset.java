package com.klu.crmbackend.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="password_resets")
public class PasswordReset {
    @Id public String tokenHash;
    public Long userId;
    public LocalDateTime expiresAt;
    public boolean used;
}
