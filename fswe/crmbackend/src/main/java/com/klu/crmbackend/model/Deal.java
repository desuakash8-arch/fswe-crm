package com.klu.crmbackend.model;

import jakarta.persistence.*;
import com.klu.crmbackend.dto.EmailDeliveryResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deal_id")
    private Long dealId;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String title;

    private BigDecimal amount;

    private String stage;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private EmailDeliveryResult emailDelivery;

    public EmailDeliveryResult getEmailDelivery() { return emailDelivery; }
    public void setEmailDelivery(EmailDeliveryResult emailDelivery) { this.emailDelivery = emailDelivery; }


    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private LocalDate closedDate;
    public LocalDate getClosedDate() { return closedDate; }
    public void setClosedDate(LocalDate value) { this.closedDate = value; }

    
    @Column(length=1000)
    private String lostReason;
    public String getLostReason() { return lostReason; }
    public void setLostReason(String value) { this.lostReason = value; }

    public Deal() {
    }

    public Long getDealId() {
        return dealId;
    }

    public void setDealId(Long dealId) {
        this.dealId = dealId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
