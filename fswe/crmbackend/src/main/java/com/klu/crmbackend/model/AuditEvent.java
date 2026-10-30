package com.klu.crmbackend.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="audit_events")
public class AuditEvent {
    public Long getId() { return id; }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String entityType;
    public Long entityId;
    public String action;
    public String actor;
    @Column(length=2000) public String details;
    public LocalDateTime createdAt=LocalDateTime.now();
}
