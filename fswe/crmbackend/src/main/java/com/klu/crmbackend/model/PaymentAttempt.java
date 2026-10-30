package com.klu.crmbackend.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="payment_attempts")
public class PaymentAttempt {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="invoice_id") public PaymentInvoice invoice;
 @Column(unique=true,nullable=false) public String paymentId;
 public String status;
 public String method;
 public long amountMinor;
 public long refundedMinor;
 public LocalDateTime updatedAt=LocalDateTime.now();
}
