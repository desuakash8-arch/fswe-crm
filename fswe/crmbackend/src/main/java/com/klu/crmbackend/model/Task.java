package com.klu.crmbackend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    private String title;

    private String description;

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private String status;

    private String priority;


    @ManyToOne
    @JoinColumn(name="interaction_id", unique=true)
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Interaction interaction;
    public Interaction getInteraction() { return interaction; }
    public void setInteraction(Interaction value) { this.interaction = value; }

    @ManyToOne
    private Customer customer;
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer value) { this.customer = value; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String reminderKey;
    public String getReminderKey() { return reminderKey; }
    public void setReminderKey(String value) { this.reminderKey = value; }

    @ManyToOne
    @JoinColumn(name="deal_id")
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private Deal deal;
    public Deal getDeal() { return deal; }
    public void setDeal(Deal value) { this.deal = value; }
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public Long getLinkedDealId() { return deal == null ? null : deal.getDealId(); }

    public Task() {
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}