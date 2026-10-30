package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.*;
import com.klu.crmbackend.repository.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api")
public class HistoryController {
    private final DealService deals;private final AuditEventRepository audit;private final EmailJobRepository emails;private final RecordAccessService access;
    public HistoryController(DealService deals,AuditEventRepository audit,EmailJobRepository emails,RecordAccessService access) {this.deals=deals;this.audit=audit;this.emails=emails;this.access=access;}
    private PageRequest page(int page,Sort sort) {if(page<0)throw new IllegalArgumentException("Invalid page");return PageRequest.of(page,20,sort);}
    private void deal(Long id) {deals.getDealById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found"));}
    private void admin() {if(!"ADMIN".equals(access.actor().getRole()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin access required");}
    @GetMapping("/deals/{id}/history") public Object history(@PathVariable Long id,@RequestParam(defaultValue="0") int page) {deal(id);return audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("DEAL",id,page(page,Sort.by(org.springframework.data.domain.Sort.Order.desc("id"))));}
    @GetMapping("/deals/{id}/emails") public Object emails(@PathVariable Long id,@RequestParam(defaultValue="0") int page) {deal(id);return emails.findByDealIdOrderByIdDesc(id,page(page,Sort.by(org.springframework.data.domain.Sort.Order.desc("id"))));}
    @GetMapping("/audit") public Object audit(@RequestParam(defaultValue="0") int page) {admin();return audit.findAll(page(page,Sort.by(org.springframework.data.domain.Sort.Order.desc("id"))));}
    @GetMapping("/emails") public Object emails(@RequestParam(defaultValue="0") int page) {admin();return emails.findAll(page(page,Sort.by(org.springframework.data.domain.Sort.Order.desc("id"))));}
    @PostMapping("/emails/{id}/retry") @org.springframework.transaction.annotation.Transactional public Object retry(@PathVariable Long id) {
        admin();var j=emails.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Email not found"));
        if(!"FAILED".equals(j.state))throw new IllegalArgumentException("Only failed messages may be retried");
        j.state="PENDING";j.attempts=0;j.nextAttempt=java.time.LocalDateTime.now();return emails.save(j);
    }
}
