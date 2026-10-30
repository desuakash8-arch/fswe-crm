package com.klu.crmbackend.service;

import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keep account writes and their notification outbox entries in one transaction. */
@Service
@Transactional
public class UserLifecycle {
    private final UserRepository users;
    private final CrmNotifications events;
    private final com.klu.crmbackend.repository.NotificationRepository notifications;
    public UserLifecycle(UserRepository users,CrmNotifications events,com.klu.crmbackend.repository.NotificationRepository notifications){this.users=users;this.events=events;this.notifications=notifications;}
    public User save(User user,CrmNotifications.State before){
        var saved=users.save(user);
        if(before==null)events.created(saved);else events.updated(saved,before);
        return saved;
    }
    public void delete(User user){var before=events.snapshot(user);notifications.deleteAll(notifications.findByUserUserId(user.getUserId()));users.delete(user);events.deleted(before);}
}
