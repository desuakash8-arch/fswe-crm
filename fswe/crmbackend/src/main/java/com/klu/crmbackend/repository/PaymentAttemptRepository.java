package com.klu.crmbackend.repository;
import com.klu.crmbackend.model.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,Long> {
 Optional<PaymentAttempt> findByPaymentId(String id);
 List<PaymentAttempt> findByInvoiceIdOrderByIdDesc(Long id);
}
