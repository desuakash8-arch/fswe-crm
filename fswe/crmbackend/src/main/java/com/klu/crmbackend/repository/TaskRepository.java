package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Task> {

    boolean existsByInteractionInteractionId(Long interactionId);
}