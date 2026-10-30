package com.klu.crmbackend.repository;
import com.klu.crmbackend.model.EmailJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.List;
public interface EmailJobRepository extends JpaRepository<EmailJob,Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EmailJob j where j.state='PENDING' and j.nextAttempt<=:now order by j.id")
    List<EmailJob> ready(LocalDateTime now, Pageable page);
    Page<EmailJob> findByDealIdOrderByIdDesc(Long dealId,Pageable page);
}
