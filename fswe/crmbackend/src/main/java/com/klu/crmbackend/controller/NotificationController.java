package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.Notification;
import com.klu.crmbackend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // GET all notifications
    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationService.getAllNotifications();
    }

    // GET notification by ID
    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotificationById(
            @PathVariable Long id) {

        return notificationService.getNotificationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // CREATE notification
    @PostMapping
    public Notification createNotification(
            @RequestBody Notification notification) {

        return notificationService.createNotification(notification);
    }

    // UPDATE notification
    @PutMapping("/{id}")
    public ResponseEntity<Notification> updateNotification(
            @PathVariable Long id,
            @RequestBody Notification notificationDetails) {

            return ResponseEntity.ok(
                    notificationService.updateNotification(
                            id, notificationDetails)
            );
    }

    // DELETE notification
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id) {

        notificationService.deleteNotification(id);

        return ResponseEntity.noContent().build();
    }
    @GetMapping("/user/{userId}")
public ResponseEntity<List<Notification>> getNotificationsByUser(
        @PathVariable Long userId
) {
    return ResponseEntity.ok(
            notificationService.getNotificationsByUser(userId)
    );
}
@GetMapping("/my")
public ResponseEntity<List<Notification>> getMyNotifications() {

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    String email = authentication.getName();

    return ResponseEntity.ok(
            notificationService.getNotificationsByUserEmail(email)
    );
}

}
