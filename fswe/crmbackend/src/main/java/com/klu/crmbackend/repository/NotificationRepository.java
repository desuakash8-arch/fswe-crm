package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Notification> {

    List<Notification> findByUserUserId(Long userId);

    List<Notification> findByUserEmail(String email);
}