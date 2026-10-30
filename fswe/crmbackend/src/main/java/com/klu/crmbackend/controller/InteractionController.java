package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Interaction;
import com.klu.crmbackend.service.InteractionService;
import com.klu.crmbackend.service.TaskService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionService interactionService;
    private final TaskService taskService;

    public InteractionController(
            InteractionService interactionService,
            TaskService taskService
    ) {
        this.interactionService = interactionService;
        this.taskService = taskService;
    }

    // GET /api/interactions
    @GetMapping
    public List<Interaction> getAllInteractions() {
        return interactionService.getAllInteractions();
    }

    // GET /api/interactions/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Interaction> getInteractionById(
            @PathVariable Long id
    ) {
        return interactionService
                .getInteractionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/interactions/customer/{customerId}
    @GetMapping("/customer/{customerId}")
    public List<Interaction> getInteractionsByCustomer(
            @PathVariable Long customerId
    ) {
        return interactionService
                .getInteractionsByCustomerId(customerId);
    }

    // POST /api/interactions
    @PostMapping
    public ResponseEntity<Interaction> createInteraction(
            @RequestBody Interaction interaction
    ) {
        Interaction savedInteraction =
                interactionService.createInteraction(interaction);

        return ResponseEntity.ok(savedInteraction);
    }

    // PUT /api/interactions/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Interaction> updateInteraction(
            @PathVariable Long id,
            @RequestBody Interaction interactionDetails
    ) {
            Interaction updatedInteraction =
                    interactionService.updateInteraction(
                            id,
                            interactionDetails
                    );

            return ResponseEntity.ok(updatedInteraction);
    }

    // DELETE /api/interactions/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInteraction(
            @PathVariable Long id
    ) {
            interactionService.deleteInteraction(id);

            return ResponseEntity
                    .noContent()
                    .build();
    }

    // POST /api/interactions/{id}/create-task
    // Creates a follow-up Task from an Interaction
    @PostMapping("/{id}/create-task")
    public ResponseEntity<?> createFollowUpTask(
            @PathVariable Long id
    ) {
            TaskService taskService = this.taskService;

            return ResponseEntity.ok(
                    taskService.createTaskFromInteraction(id)
            );
    }
}