package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Customer;
import com.klu.crmbackend.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }
    @GetMapping
    public List<Customer> list(Authentication auth) { return service.getCustomersForUser(auth); }
    @GetMapping("/{id}")
    public ResponseEntity<Customer> get(@PathVariable Long id, Authentication auth) {
        return service.getCustomerByIdForUser(id, auth).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    @PostMapping
    public Customer create(@RequestBody Customer customer, Authentication auth) { return service.createCustomer(customer, auth); }
    @PutMapping("/{id}")
    public Customer update(@PathVariable Long id, @RequestBody Customer customer, Authentication auth) {
        return service.updateCustomer(id, customer, auth);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
