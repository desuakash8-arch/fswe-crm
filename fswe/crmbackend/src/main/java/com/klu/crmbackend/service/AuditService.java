package com.klu.crmbackend.service;
import com.klu.crmbackend.model.AuditEvent;
import com.klu.crmbackend.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
@Service
public class AuditService {
    private final AuditEventRepository events;
    public AuditService(AuditEventRepository events) { this.events=events; }
    public void record(String type,Long id,String action,String details) {
        var e=new AuditEvent();e.entityType=type;e.entityId=id;e.action=action;e.details=details;
        var auth=SecurityContextHolder.getContext().getAuthentication();e.actor=auth==null?"SYSTEM":auth.getName();events.save(e);
    }
}
