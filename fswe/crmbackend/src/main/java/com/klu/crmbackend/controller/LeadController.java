package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Customer;
import com.klu.crmbackend.model.Lead;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.CustomerRepository;
import com.klu.crmbackend.repository.LeadRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final com.klu.crmbackend.service.CrmNotifications events;

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final com.klu.crmbackend.service.DealService deals;
    private final com.klu.crmbackend.service.AuditService audit;
    private final LeadRepository leadRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public LeadController(LeadRepository leadRepository, CustomerRepository customerRepository,
                          UserRepository userRepository, com.klu.crmbackend.service.DealService deals, com.klu.crmbackend.service.AuditService audit, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.deals=deals;this.audit=audit;
        this.leadRepository = leadRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    private User actor(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new IllegalStateException("Logged-in user not found."));
    }
    private String role(User user) { return user.getRole() == null ? "" : user.getRole().toUpperCase(); }
    private boolean sameCompany(User a, User b) {
        return a.getCompany() != null && b.getCompany() != null
            && a.getCompany().getCompanyId().equals(b.getCompany().getCompanyId());
    }
    private boolean canAccess(User actor, Lead lead) {
        if ("ADMIN".equals(role(actor))) return true;
        User assigned = lead.getAssignedTo();
        if (assigned == null) return false;
        if ("SALES".equals(role(actor))) return actor.getUserId().equals(assigned.getUserId());
        return "MANAGER".equals(role(actor)) && sameCompany(actor, assigned)
            && ("SALES".equals(role(assigned)) || actor.getUserId().equals(assigned.getUserId()));
    }
    private User resolveAssignment(Lead lead, User actor) {
        if ("SALES".equals(role(actor))) return actor;
        if (lead.getAssignedTo() == null) return actor;
        Long assignedId = lead.getAssignedTo().getUserId();
        User assigned = userRepository.findById(assignedId)
            .orElseThrow(() -> new IllegalArgumentException("Assigned user not found."));
        if (!"ADMIN".equals(role(actor)) && (!"SALES".equals(role(assigned)) || !sameCompany(actor, assigned)))
            throw new IllegalArgumentException("You can assign leads only to Sales users in your company.");
        return assigned;
    }

    @GetMapping
    public List<Lead> getAllLeads(Authentication authentication) {
        User user = actor(authentication);
        return leadRepository.findAll(com.klu.crmbackend.service.RecordQuery.visible(user,"assignedTo"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lead> getLeadById(@PathVariable Long id, Authentication authentication) {
        User user = actor(authentication);
        return leadRepository.findById(id)
            .filter(lead -> canAccess(user, lead))
            .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createLead(@RequestBody Lead lead, Authentication authentication) {
        User user = actor(authentication);
        try {
            com.klu.crmbackend.service.Rules.lead(lead);
            if("CONVERTED".equals(lead.getStatus())) throw new IllegalArgumentException("Use the Convert action to create a linked customer");
            lead.setLeadId(null);
            lead.setAssignedTo(resolveAssignment(lead, user));
            leadRepository.save(lead);events.created(lead);return ResponseEntity.status(HttpStatus.CREATED).body(lead);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateLead(@PathVariable Long id, @RequestBody Lead details,
                                        Authentication authentication) {
        User user = actor(authentication);
        Lead lead = leadRepository.findById(id).orElse(null);
        if (lead == null) return ResponseEntity.notFound().build();
        if (!canAccess(user, lead)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You cannot edit this lead."));
        try {
            com.klu.crmbackend.service.Rules.lead(details);
            if(!java.util.Objects.equals(lead.getStatus(),details.getStatus()) && ("CONVERTED".equals(lead.getStatus()) || "CONVERTED".equals(details.getStatus()))) throw new IllegalArgumentException("Use the Convert action; converted leads cannot change status");
            var before=events.snapshot(lead);
            lead.setName(details.getName());
            lead.setEmail(details.getEmail());
            lead.setPhone(details.getPhone());
            lead.setCompany(details.getCompany());
            lead.setSource(details.getSource());
            lead.setStatus(details.getStatus());
            if ("ADMIN".equals(role(user)) || "MANAGER".equals(role(user))) {
                if (details.getAssignedTo() != null) {
                    lead.setAssignedTo(resolveAssignment(details, user));
                }
            } else {
                lead.setAssignedTo(user);
            }
            leadRepository.save(lead);events.updated(lead,before);return ResponseEntity.ok(lead);
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteLead(@PathVariable Long id, Authentication authentication) {
        User user = actor(authentication);
        if (!"ADMIN".equals(role(user)) && !"MANAGER".equals(role(user)))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Sales users cannot delete leads."));
        Lead lead = leadRepository.findById(id).orElse(null);
        if (lead == null) return ResponseEntity.notFound().build();
        if (!"ADMIN".equals(role(user)) && !canAccess(user, lead))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You cannot delete this lead."));
        var before=events.snapshot(lead);leadRepository.delete(lead);events.deleted(before);
        return ResponseEntity.noContent().build();
    }

    public record ConversionRequest(boolean createDeal,String title,java.math.BigDecimal amount) {}
    @PostMapping("/{id}/convert")
    @Transactional
    public ResponseEntity<?> convertLeadToCustomer(@PathVariable Long id, Authentication authentication, @RequestBody(required=false) ConversionRequest options) {
        User user = actor(authentication);
        Lead lead = leadRepository.findById(id).orElse(null);
        if (lead == null || !canAccess(user, lead)) return ResponseEntity.notFound().build();
        em.refresh(lead,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!canAccess(user,lead)) return ResponseEntity.notFound().build();
        if ("CONVERTED".equalsIgnoreCase(lead.getStatus()))
            return ResponseEntity.badRequest().body(Map.of("message", "This lead has already been converted."));
        if (lead.getEmail() != null && !customerRepository.findAll(com.klu.crmbackend.service.RecordQuery.<Customer>visible(user,"assignedTo").and((r,q,cb)->cb.equal(cb.lower(r.get("email")),lead.getEmail().toLowerCase()))).isEmpty())
            return ResponseEntity.badRequest().body(Map.of("message", "A customer with this email already exists."));
        var before=events.snapshot(lead);
        Customer customer = new Customer();
        customer.setName(lead.getName());
        customer.setEmail(lead.getEmail());
        customer.setPhone(lead.getPhone());
        customer.setCompany(lead.getCompany());
        customer.setAddress("");
        customer.setStatus("ACTIVE");
        customer.setAssignedTo(lead.getAssignedTo());
        Customer saved = customerRepository.save(customer);
        lead.setStatus("CONVERTED");
        lead.setConvertedCustomer(saved);lead.setConvertedAt(java.time.LocalDateTime.now());
        leadRepository.save(lead);
        var response=new java.util.HashMap<String,Object>();response.put("customerId",saved.getCustomerId());response.put("leadId",lead.getLeadId());response.put("message","Lead converted successfully");
        if(options!=null && options.createDeal()) {
            var deal=new com.klu.crmbackend.model.Deal();deal.setCustomer(saved);deal.setAssignedTo(lead.getAssignedTo());deal.setStage("LEAD");
            deal.setTitle(options.title()==null || options.title().isBlank()?lead.getName()+" opportunity":options.title());deal.setAmount(options.amount()==null?java.math.BigDecimal.ZERO:options.amount());
            response.put("dealId",deals.createDeal(deal).getDealId());
        }
        events.created(saved);events.updated(lead,before);
        audit.record("LEAD",id,"CONVERTED","Customer #"+saved.getCustomerId());return ResponseEntity.ok(response);
    }
}
