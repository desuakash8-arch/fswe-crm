package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Interaction;
import com.klu.crmbackend.model.Task;
import com.klu.crmbackend.repository.InteractionRepository;
import com.klu.crmbackend.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@org.springframework.transaction.annotation.Transactional
public class TaskService {
    private final com.klu.crmbackend.service.CrmNotifications events;


    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final RecordAccessService access;

    private final TaskRepository taskRepository;
    private final InteractionRepository interactionRepository;

    public TaskService(
            TaskRepository taskRepository,
            InteractionRepository interactionRepository,
            RecordAccessService access, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.access = access;
        this.taskRepository = taskRepository;
        this.interactionRepository = interactionRepository;
    }

    // Get all tasks
    public List<Task> getAllTasks() {
        var actor = access.actor();
        return taskRepository.findAll(RecordQuery.visible(actor,"assignedTo"), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("dueDate")));
    }

    // Get task by ID
    public Optional<Task> getTaskById(Long id) {
        return taskRepository.findById(id).filter(record -> access.canAccess(record.getAssignedTo()));
    }

    // Create task
    public Task createTask(Task task) {
        Rules.task(task);
        task.setCustomer(access.resolveCustomer(task.getCustomer()));
        task.setDeal(access.resolveDeal(task.getDeal(),task.getCustomer()));
        task.setTaskId(null);
        task.setAssignedTo(access.resolveAssignment(task.getAssignedTo()));
        taskRepository.save(task);events.created(task);return task;
    }

 // Update task
public Task updateTask(
        Long id,
        Task taskDetails
) {

    return taskRepository.findById(id)
            .map(task -> {

                access.requireAccess(task.getAssignedTo());
                var before=events.snapshot(task);
                Rules.task(taskDetails);
                if(taskDetails.getAssignedTo()!=null) task.setAssignedTo(access.resolveAssignment(taskDetails.getAssignedTo()));
                if(taskDetails.getCustomer()!=null) task.setCustomer(access.resolveCustomer(taskDetails.getCustomer()));
                if(task.getInteraction()!=null && taskDetails.getCustomer()!=null && !task.getInteraction().getCustomer().getCustomerId().equals(taskDetails.getCustomer().getCustomerId())) throw new IllegalArgumentException("A follow-up must keep its interaction customer");
                var linkedDeal = taskDetails.getDeal()!=null ? taskDetails.getDeal() : task.getDeal();
                task.setDeal(access.resolveDeal(linkedDeal,task.getCustomer()));
                if(task.getInteraction()!=null && !java.util.Objects.equals(task.getDeal()==null?null:task.getDeal().getDealId(),task.getInteraction().getDeal()==null?null:task.getInteraction().getDeal().getDealId())) throw new IllegalArgumentException("A follow-up must keep its interaction deal");
                if(!java.util.Objects.equals(task.getDueDate(),taskDetails.getDueDate()) || !java.util.Objects.equals(task.getStatus(),taskDetails.getStatus())) task.setReminderKey(null);
                // Update task fields
                task.setTitle(taskDetails.getTitle());
                task.setDescription(taskDetails.getDescription());

                task.setDueDate(taskDetails.getDueDate());
                task.setStatus(taskDetails.getStatus());
                task.setPriority(taskDetails.getPriority());

                // Save updated task
                Task updatedTask = taskRepository.save(task);

                events.updated(updatedTask,before);return updatedTask;
            })
            .orElseThrow(() ->
                    new IllegalArgumentException("Task not found")
            );
}
    // Delete task
    public void deleteTask(Long id) {

        if (!taskRepository.existsById(id)) {
            throw new IllegalArgumentException("Task not found");
        }

        var record = taskRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Record not found"));
        access.requireAccess(record.getAssignedTo());
        var before=events.snapshot(record);taskRepository.deleteById(id);events.deleted(before);
    }

    // Create task from interaction follow-up
    public Task createTaskFromInteraction(Long interactionId) {

        // Find interaction
        Interaction interaction =
                interactionRepository
                        .findById(interactionId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Interaction not found"
                                )
                        );

        access.requireAccess(interaction.getUser());
        em.lock(interaction, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        // Check follow-up date
        if (interaction.getNextFollowup() == null) {

            throw new IllegalArgumentException(
                    "This interaction does not have a follow-up date"
            );
        }

        /*
         * Create the task title.
         *
         * If the interaction is connected to a customer:
         *     Follow up with Customer Name
         *
         * Otherwise:
         *     Customer follow-up
         */
        String taskTitle;

        if (interaction.getCustomer() != null) {

            taskTitle =
                    "Follow up with "
                            + interaction.getCustomer().getName();

        } else {

            taskTitle = "Customer follow-up";
        }

        /*
         * Prevent duplicate follow-up tasks.
         *
         * We check whether a task with the same title
         * and due date already exists.
         */
        boolean taskExists = taskRepository.existsByInteractionInteractionId(interactionId);

        if (taskExists) {

            throw new IllegalArgumentException(
                    "A follow-up task already exists for this interaction"
            );
        }

        // Create new task
        Task task = new Task();
        task.setInteraction(interaction);
        task.setCustomer(interaction.getCustomer());
        task.setDeal(access.resolveDeal(interaction.getDeal(),interaction.getCustomer()));

        // Task title
        task.setTitle(taskTitle.substring(0,Math.min(255,taskTitle.length())));

        // Task description
        task.setDescription(
                ("Follow-up created from interaction: " + java.util.Objects.toString(interaction.getNotes(), "")).substring(0, Math.min(255, ("Follow-up created from interaction: " + java.util.Objects.toString(interaction.getNotes(), "")).length()))
        );

        // Assign task to employee who created interaction
        task.setAssignedTo(
                interaction.getUser()
        );

        // Convert LocalDateTime → LocalDate
        task.setDueDate(
                interaction
                        .getNextFollowup()
                        .toLocalDate()
        );

        // Default status
        task.setStatus("PENDING");

        // Default priority
        task.setPriority("HIGH");

        // Save task
        Task savedTask =
                taskRepository.save(task);

        events.event(savedTask.getAssignedTo(),"TASK","Follow-up task created",
            "New follow-up task assigned: "+savedTask.getTitle(),
            "Reason: A follow-up task was created from interaction #"+interaction.getInteractionId()
            +".\nInteraction type: "+interaction.getType()+"\nInteraction notes: "+interaction.getNotes()
            +"\nDue date: "+savedTask.getDueDate()+"\nCustomer: "+(interaction.getCustomer()==null?"Not selected":interaction.getCustomer().getName()),savedTask.getLinkedDealId());

        return savedTask;
    }
}