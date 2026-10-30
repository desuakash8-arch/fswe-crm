package com.klu.crmbackend.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="payment_invoices")
public class PaymentInvoice {
    public Long getId() { return id; }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @ManyToOne(optional=false) @JoinColumn(name="deal_id",unique=true) public Deal deal;
 public String title;
 public String customerName;
 public String customerEmail;
 public long amountMinor;
 public String currency="INR";
 public String status="UNPAID";
 @Column(unique=true,length=64) public String tokenHash;
 public LocalDateTime linkExpiresAt;
 @Column(unique=true) public String receipt;
 @Column(unique=true) public String orderId;
 public String keyId;
 public boolean orderAttempted;
 @Column(unique=true) public String paymentId;
 public LocalDateTime createdAt=LocalDateTime.now();
 public LocalDateTime paidAt;
}
