package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteractionRepository
        extends JpaRepository<Interaction, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Interaction> {

    List<Interaction> findByCustomerCustomerId(Long customerId);
}