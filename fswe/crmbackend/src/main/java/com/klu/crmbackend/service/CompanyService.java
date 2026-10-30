
package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Company;
import com.klu.crmbackend.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@org.springframework.transaction.annotation.Transactional
public class CompanyService {
    private final com.klu.crmbackend.service.CrmNotifications events;


    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.companyRepository = companyRepository;
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    public Company createCompany(Company company) {
        company.setCompanyId(null);
        companyRepository.save(company);events.created(company);return company;
    }

    public Company getCompanyById(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Company not found"));
    }

public Company updateCompany(Long id, Company details) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Company not found: " + id));

        var before=events.snapshot(company);
        company.setName(details.getName());
        company.setEmail(details.getEmail());
        company.setPhone(details.getPhone());
        company.setAddress(details.getAddress());
        company.setStatus(details.getStatus());

        companyRepository.save(company);events.updated(company,before);return company;
    }

public void deleteCompany(Long id) {
    Company company = companyRepository.findById(id)
            .orElseThrow(() ->
                    new RuntimeException("Company not found: " + id));

    var before=events.snapshot(company);companyRepository.delete(company);events.deleted(before);
}

}