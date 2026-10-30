package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Deal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Deal> {
}