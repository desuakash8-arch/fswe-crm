package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Lead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Lead> {

}