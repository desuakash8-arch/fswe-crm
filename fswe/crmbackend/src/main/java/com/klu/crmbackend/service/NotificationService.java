package com.klu.crmbackend.service;

import com.klu.crmbackend.model.Notification;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.NotificationRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@org.springframework.transaction.annotation.Transactional
public class NotificationService {
    private final com.klu.crmbackend.service.CrmNotifications events;


    private final RecordAccessService access;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            RecordAccessService access, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;
        this.access = access;

        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // GET all notifications
    public List<Notification> getAllNotifications() {
        var actor = access.actor();
        return notificationRepository.findAll(RecordQuery.visible(actor,"user"), org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt")));
    }

    // GET notification by ID
    public Optional<Notification> getNotificationById(Long id) {
        return notificationRepository.findById(id).filter(record -> access.canAccess(record.getUser()));
    }

    // CREATE notification
    public Notification createNotification(Notification notification) {

        notification.setNotificationId(null);
        notification.setUser(access.resolveAssignment(notification.getUser()));
        if (notification.getUser() != null) {

            Long userId = notification.getUser().getUserId();

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("User not found"));

            notification.setUser(user);
        }

        events.publish(notification,null,null);return notification;
    }

    // UPDATE notification
    public Notification updateNotification(
            Long id,
            Notification notificationDetails) {

        Notification notification =
                notificationRepository.findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Notification not found"));

        access.requireAccess(notification.getUser());
        if(notificationDetails.getUser()!=null) notificationDetails.setUser(access.resolveAssignment(notificationDetails.getUser()));
        notification.setTitle(notificationDetails.getTitle());
        notification.setType(notificationDetails.getType());
        notification.setMessage(notificationDetails.getMessage());
        notification.setIsRead(notificationDetails.getIsRead());

        if (notificationDetails.getUser() != null) {

            Long userId =
                    notificationDetails.getUser().getUserId();

            User user = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("User not found"));

            notification.setUser(user);
        }

        return notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserUserId(userId).stream()
            .filter(record -> access.canAccess(record.getUser())).toList();
    }
    public List<Notification> getNotificationsByUserEmail(String email) {

    return notificationRepository.findByUserEmail(email);
}

    // DELETE notification
    public void deleteNotification(Long id) {
        var record = notificationRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Record not found"));
        access.requireAccess(record.getUser());
        notificationRepository.deleteById(id);
    }
    public Notification markAsRead(Long id) {

    return notificationRepository
            .findById(id)
            .map(notification -> {
                access.requireAccess(notification.getUser());

                notification.setIsRead(true);

                return notificationRepository.save(
                        notification
                );

            })
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Notification not found"
                    )
            );
}
}