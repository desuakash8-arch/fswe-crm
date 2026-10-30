
package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository
        extends JpaRepository<Company, Long> {
}
