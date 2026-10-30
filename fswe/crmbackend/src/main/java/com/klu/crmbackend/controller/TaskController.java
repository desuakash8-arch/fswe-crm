package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Task;
import com.klu.crmbackend.service.TaskService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // GET /api/tasks
    // Get all tasks
    @GetMapping
    public List<Task> getAllTasks() {
        return taskService.getAllTasks();
    }

    // GET /api/tasks/{id}
    // Get task by ID
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(
            @PathVariable Long id
    ) {

        return taskService
                .getTaskById(id)
                .map(ResponseEntity::ok)
                .orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // POST /api/tasks
    // Create task
    @PostMapping
    public ResponseEntity<Task> createTask(
            @RequestBody Task task
    ) {

        Task savedTask =
                taskService.createTask(task);

        return ResponseEntity.ok(savedTask);
    }

    // PUT /api/tasks/{id}
    // Update task
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(
            @PathVariable Long id,
            @RequestBody Task taskDetails
    ) {

            Task updatedTask =
                    taskService.updateTask(
                            id,
                            taskDetails
                    );

            return ResponseEntity.ok(updatedTask);
    }

    // DELETE /api/tasks/{id}
    // Delete task
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Long id
    ) {

            taskService.deleteTask(id);

            return ResponseEntity
                    .noContent()
                    .build();
    }
}