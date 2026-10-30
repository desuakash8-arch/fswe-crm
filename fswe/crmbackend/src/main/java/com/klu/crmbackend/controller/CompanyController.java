package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Company;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.UserRepository;
import com.klu.crmbackend.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companyService;
    private final UserRepository userRepository;

    public CompanyController(CompanyService companyService, UserRepository userRepository) {
        this.companyService = companyService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<Company>> getAllCompanies(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) {
            return ResponseEntity.ok(companyService.getAllCompanies());
        }
        if ("MANAGER".equalsIgnoreCase(actor.getRole()) && actor.getCompany() != null) {
            return ResponseEntity.ok(List.of(actor.getCompany()));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/available")
    public ResponseEntity<List<Company>> getAvailableCompanies(Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return ResponseEntity.ok(companyService.getAllCompanies());
        return ResponseEntity.ok(actor.getCompany() == null ? List.of() : List.of(actor.getCompany()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Company> getCompanyById(@PathVariable Long id, Authentication authentication) {
        User actor = userRepository.findByEmail(authentication.getName()).orElseThrow();
        try {
            Company company = companyService.getCompanyById(id);
            if ("ADMIN".equalsIgnoreCase(actor.getRole()) ||
                    ("MANAGER".equalsIgnoreCase(actor.getRole()) && actor.getCompany() != null &&
                     actor.getCompany().getCompanyId().equals(company.getCompanyId()))) {
                return ResponseEntity.ok(company);
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Company> createCompany(@RequestBody Company company) {
        Company saved = companyService.createCompany(company);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Company> updateCompany(@PathVariable Long id, @RequestBody Company company) {
        return ResponseEntity.ok(companyService.updateCompany(id, company));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
        return ResponseEntity.noContent().build();
    }
}
