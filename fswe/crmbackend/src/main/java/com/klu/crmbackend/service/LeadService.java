package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Lead;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.LeadRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@org.springframework.transaction.annotation.Transactional
public class LeadService {
    private final com.klu.crmbackend.service.CrmNotifications events;


    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    public LeadService(LeadRepository leadRepository,
                       UserRepository userRepository, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
    }

    // GET all leads
    public List<Lead> getAllLeads() {
        return leadRepository.findAll();
    }

    // GET lead by ID
    public Optional<Lead> getLeadById(Long id) {
        return leadRepository.findById(id);
    }

    // CREATE lead
    public Lead createLead(Lead lead) {

        if (lead.getAssignedTo() != null) {

            Long userId = lead.getAssignedTo().getUserId();

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new RuntimeException("Assigned user not found"));

            lead.setAssignedTo(user);
        }

        leadRepository.save(lead);events.created(lead);return lead;
    }

    // UPDATE lead
    public Lead updateLead(Long id, Lead leadDetails) {

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Lead not found"));

        var before=events.snapshot(lead);
        lead.setName(leadDetails.getName());
        lead.setEmail(leadDetails.getEmail());
        lead.setPhone(leadDetails.getPhone());
        lead.setCompany(leadDetails.getCompany());
        lead.setSource(leadDetails.getSource());
        lead.setStatus(leadDetails.getStatus());

        if (leadDetails.getAssignedTo() != null) {

            Long userId = leadDetails.getAssignedTo().getUserId();

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new RuntimeException("Assigned user not found"));

            lead.setAssignedTo(user);
        }

        leadRepository.save(lead);events.updated(lead,before);return lead;
    }

    // DELETE lead
    public void deleteLead(Long id) {
        var lead=leadRepository.findById(id).orElseThrow();var before=events.snapshot(lead);leadRepository.deleteById(id);events.deleted(before);
    }
}