package com.klu.crmbackend.repository;
import com.klu.crmbackend.model.PaymentInvoice;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice,Long>,JpaSpecificationExecutor<PaymentInvoice> {
 boolean existsByDealDealId(Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from PaymentInvoice i where i.id=:id") Optional<PaymentInvoice> lock(Long id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PaymentInvoice> findByTokenHash(String tokenHash);
 @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PaymentInvoice> findByOrderId(String orderId);
}
