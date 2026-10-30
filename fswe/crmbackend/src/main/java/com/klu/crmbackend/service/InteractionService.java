
package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Interaction;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.InteractionRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@org.springframework.transaction.annotation.Transactional
public class InteractionService {
    private final com.klu.crmbackend.service.CrmNotifications events;

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

    private final RecordAccessService access;

    private final InteractionRepository interactionRepository;
    private final UserRepository userRepository;

    public InteractionService(
            InteractionRepository interactionRepository,
            UserRepository userRepository
    ,
            RecordAccessService access, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.access = access;
        this.interactionRepository = interactionRepository;
        this.userRepository = userRepository;
    }

    public List<Interaction> getAllInteractions() {
        var actor = access.actor();
        return interactionRepository.findAll(RecordQuery.visible(actor,"user"), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("interactionDate")));
    }

    public Optional<Interaction> getInteractionById(Long id) {
        return interactionRepository.findById(id).filter(record -> access.canAccess(record.getUser()));
    }

    public Interaction createInteraction(Interaction interaction) {
        Rules.interaction(interaction);
        interaction.setInteractionId(null);
        interaction.setUser(access.resolveAssignment(interaction.getUser()));
        interaction.setCustomer(access.resolveCustomer(interaction.getCustomer()));
        interaction.setDeal(access.resolveDeal(interaction.getDeal(), interaction.getCustomer()));
        if (interaction.getUser() == null ||
                interaction.getUser().getUserId() == null) {
            throw new IllegalArgumentException(
                    "A user must be assigned to the interaction"
            );
        }

        Long userId = interaction.getUser().getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId)
                );

        interaction.setUser(user);

        interactionRepository.save(interaction);events.created(interaction);return interaction;
    }

    public Interaction updateInteraction(
            Long id,
            Interaction interactionDetails
    ) {
        return interactionRepository.findById(id)
                .map(interaction -> {
                    access.requireAccess(interaction.getUser());
                    var before=events.snapshot(interaction);
                    Rules.interaction(interactionDetails);
                    interactionDetails.setUser(access.resolveAssignment(interactionDetails.getUser()));
                    interactionDetails.setCustomer(access.resolveCustomer(interactionDetails.getCustomer()));
                    if(interaction.getDeal()!=null && interactionDetails.getDeal()!=null && !interaction.getDeal().getDealId().equals(interactionDetails.getDeal().getDealId())) throw new IllegalArgumentException("An interaction cannot move between deals; create a new interaction instead");
                    var linkedDeal = interactionDetails.getDeal() != null ? interactionDetails.getDeal() : interaction.getDeal();
                    interaction.setDeal(access.resolveDeal(linkedDeal, interactionDetails.getCustomer()));
                    for(var task:em.createQuery("select t from Task t where t.interaction.interactionId=:id",com.klu.crmbackend.model.Task.class).setParameter("id",id).getResultList()) {
                        if(task.getCustomer()!=null && (interactionDetails.getCustomer()==null || !task.getCustomer().getCustomerId().equals(interactionDetails.getCustomer().getCustomerId()))) throw new IllegalArgumentException("An interaction with a follow-up must keep its customer");
                        task.setDeal(interaction.getDeal());
                    }
                    interaction.setCustomer(
                            interactionDetails.getCustomer()
                    );

                    interaction.setUser(
                            interactionDetails.getUser()
                    );

                    interaction.setType(
                            interactionDetails.getType()
                    );

                    interaction.setNotes(
                            interactionDetails.getNotes()
                    );

                    interaction.setInteractionDate(
                            interactionDetails.getInteractionDate()
                    );

                    interaction.setNextFollowup(
                            interactionDetails.getNextFollowup()
                    );

                    interactionRepository.save(interaction);events.updated(interaction,before);return interaction;
                })
                .orElseThrow(() ->
                        new IllegalArgumentException("Interaction not found")
                );
    }

    public void deleteInteraction(Long id) {
        var record = interactionRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Record not found"));
        access.requireAccess(record.getUser());
        var before=events.snapshot(record);interactionRepository.deleteById(id);events.deleted(before);
    }

    public List<Interaction> getInteractionsByCustomerId(Long customerId) {
        return interactionRepository
                .findByCustomerCustomerId(customerId).stream()
                .filter(record -> access.canAccess(record.getUser())).toList();
    }
}
