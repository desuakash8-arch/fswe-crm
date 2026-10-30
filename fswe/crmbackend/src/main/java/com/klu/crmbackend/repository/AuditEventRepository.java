package com.klu.crmbackend.repository;
import com.klu.crmbackend.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long> {
    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String type,Long id,Pageable page);
}
