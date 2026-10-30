package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Customer;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.CustomerRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordAccessService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final UserRepository users;
    private final CustomerRepository customers;
    public RecordAccessService(UserRepository users, CustomerRepository customers) {
        this.users = users;
        this.customers = customers;
    }
    public User actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
        return users.findByEmail(auth.getName()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }
    public boolean sameCompany(User a, User b) {
        return a.getCompany() != null && b != null && b.getCompany() != null &&
            a.getCompany().getCompanyId().equals(b.getCompany().getCompanyId());
    }
    public boolean canAccess(User actor, User assigned) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return true;
        if (assigned == null) return false;
        if (actor.getUserId().equals(assigned.getUserId())) return true;
        return "MANAGER".equalsIgnoreCase(actor.getRole()) && sameCompany(actor, assigned)
            && "SALES".equalsIgnoreCase(assigned.getRole());
    }
    public boolean canAccess(User assigned) { return canAccess(actor(), assigned); }
    public void requireAccess(User assigned) {
        if (!canAccess(assigned)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this record");
    }
    public User resolveAssignment(User requested) {
        User actor = actor();
        if ("SALES".equalsIgnoreCase(actor.getRole())) return actor;
        if (requested == null) return actor;
        if (requested.getUserId() == null) throw new IllegalArgumentException("Select an assigned user");
        User assigned = users.findById(requested.getUserId()).orElseThrow(() ->
            new IllegalArgumentException("Assigned user not found"));
        if(!assigned.getActive()) throw new IllegalArgumentException("Assigned employee is inactive");
        if (!canAccess(actor, assigned)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Assign records only to yourself or Sales users in your company");
        return assigned;
    }
    public Customer resolveCustomer(Customer requested) {
        if (requested == null) return null;
        if (requested.getCustomerId() == null) throw new IllegalArgumentException("Select a customer");
        Customer customer = customers.findById(requested.getCustomerId()).orElseThrow(() ->
            new IllegalArgumentException("Customer not found"));
        requireAccess(customer.getAssignedTo());
        return customer;
    }
    public com.klu.crmbackend.model.Deal resolveDeal(com.klu.crmbackend.model.Deal requested, Customer customer) {
        if (requested == null) return null;
        if (requested.getDealId() == null) throw new IllegalArgumentException("Select a valid deal");
        var deal = em.find(com.klu.crmbackend.model.Deal.class, requested.getDealId());
        if (deal == null || !canAccess(deal.getAssignedTo()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal not found");
        if (deal.getCustomer() == null || customer == null || !deal.getCustomer().getCustomerId().equals(customer.getCustomerId()))
            throw new IllegalArgumentException("The selected deal must belong to this customer");
        return deal;
    }
}
