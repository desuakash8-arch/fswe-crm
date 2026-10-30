package com.klu.crmbackend.controller;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.service.*;
import com.klu.crmbackend.repository.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestController @RequestMapping("/api/customers")
public class CustomerDetailController {
    private final CustomerRepository customers;private final LeadRepository leads;private final RecordAccessService access;
    public CustomerDetailController(CustomerRepository customers,LeadRepository leads,RecordAccessService access){this.customers=customers;this.leads=leads;this.access=access;}
    @GetMapping("/{id}/detail") public Object detail(@PathVariable Long id) {
        var c=customers.findById(id).filter(v->access.canAccess(v.getAssignedTo())).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found"));
        var source=leads.findAll(RecordQuery.<Lead>visible(access.actor(),"assignedTo").and((r,q,cb)->cb.equal(r.get("convertedCustomer").get("customerId"),id)));
        return Map.of("customer",c,"sourceLeads",source);
    }
}
