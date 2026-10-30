package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.time.LocalDate;
@Service
public class ReminderService {
    private final com.klu.crmbackend.service.CrmNotifications events;

    @PersistenceContext private EntityManager em;

    public ReminderService(com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;}
    @Transactional public void run() {
        LocalDate today=LocalDate.now();
        var tasks=em.createQuery("select t from Task t where t.status in ('PENDING','IN_PROGRESS') and t.dueDate<=:tomorrow and t.assignedTo.active=true",Task.class)
            .setParameter("tomorrow",today.plusDays(1)).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for(var t:tasks) {
            String type=t.getDueDate().isBefore(today)?"OVERDUE":t.getDueDate().equals(today)?"DUE_TODAY":"UPCOMING";
            String key=t.getDueDate()+":"+type;
            if(key.equals(t.getReminderKey())) continue;
            var n=new Notification();n.setUser(t.getAssignedTo());n.setTitle("Task reminder");n.setType(type);
            String message=type.replace('_',' ')+": "+t.getTitle();n.setMessage(message.substring(0,Math.min(255,message.length())));n.setIsRead(false);
            events.publish(n,"Reason: The task is "+type.replace('_',' ').toLowerCase()+" and is still "+t.getStatus()+".\nTask: "+t.getTitle()+" (#"+t.getTaskId()+")\nDue date: "+t.getDueDate()+"\nDescription: "+java.util.Objects.toString(t.getDescription(),"Not recorded"),t.getLinkedDealId());
            t.setReminderKey(key);
        }
    }
}
