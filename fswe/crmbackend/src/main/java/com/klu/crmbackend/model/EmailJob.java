package com.klu.crmbackend.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
@Entity @Table(name="email_jobs")
public class EmailJob {
    public Long getId() { return id; }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String recipient;
    public String subject;
    @Lob @JsonIgnore public String body;
    public String state="PENDING";
    public int attempts;
    public Long dealId;
    public Long ownerId;
    public LocalDateTime createdAt=LocalDateTime.now();
    public LocalDateTime nextAttempt=LocalDateTime.now();
    public LocalDateTime acceptedAt;
    public String lastError;
}
