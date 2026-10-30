package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Customer;
import com.klu.crmbackend.repository.CustomerRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CustomerService {
    private final com.klu.crmbackend.service.CrmNotifications events;

    private final CustomerRepository customers;
    private final RecordAccessService access;
    public CustomerService(CustomerRepository customers, RecordAccessService access, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.customers = customers;
        this.access = access;
    }
    public List<Customer> getCustomersForUser(Authentication authentication) {
        var actor = access.actor();
        return customers.findAll(RecordQuery.visible(actor,"assignedTo"));
    }
    public Optional<Customer> getCustomerByIdForUser(Long id, Authentication authentication) {
        return customers.findById(id).filter(c -> access.canAccess(c.getAssignedTo()));
    }
    public Optional<Customer> getCustomerById(Long id) { return customers.findById(id); }
    public Customer createCustomer(Customer customer, Authentication authentication) {
        customer.setCustomerId(null);
        customer.setAssignedTo(access.resolveAssignment(customer.getAssignedTo()));
        Rules.customer(customer); checkDuplicate(customer, null);
        customers.save(customer);events.created(customer);return customer;
    }
    public Customer updateCustomer(Long id, Customer details, Authentication authentication) {
        Customer customer = customers.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        access.requireAccess(customer.getAssignedTo());
        var before=events.snapshot(customer);
        Rules.customer(details); checkDuplicate(details, id);
        if (details.getAssignedTo() != null) customer.setAssignedTo(access.resolveAssignment(details.getAssignedTo()));
        customer.setName(details.getName());
        customer.setEmail(details.getEmail());
        customer.setPhone(details.getPhone());
        customer.setCompany(details.getCompany());
        customer.setAddress(details.getAddress());
        customer.setStatus(details.getStatus());
        customers.save(customer);events.updated(customer,before);return customer;
    }
    private void checkDuplicate(Customer value,Long id) {
        if(value.getEmail()==null) return;
        var matches=customers.findAll(RecordQuery.<Customer>visible(access.actor(),"assignedTo").and((r,q,cb)->cb.equal(cb.lower(r.get("email")),value.getEmail())));
        if(matches.stream().anyMatch(c->!c.getCustomerId().equals(id))) throw new ResponseStatusException(HttpStatus.CONFLICT,"A visible customer with this email already exists");
    }
    public void deleteCustomer(Long id) { var customer=customers.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found"));var before=events.snapshot(customer);customers.delete(customer);events.deleted(before); }
}
