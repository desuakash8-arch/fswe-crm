package com.klu.crmbackend.service;

import com.klu.crmbackend.dto.EmailDeliveryResult;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

/** One transactional path for new in-app notifications and their email outbox entries. */
@Service
@Transactional
public class CrmNotifications {
    private final NotificationRepository notifications;
    private final EmailQueue queue;
    private final RecordAccessService access;
    public CrmNotifications(NotificationRepository notifications, EmailQueue queue, RecordAccessService access) {
        this.notifications=notifications;this.queue=queue;this.access=access;
    }
    public EmailDeliveryResult publish(Notification n,String details,Long dealId) {
        if(n.getNotificationId()!=null) throw new IllegalArgumentException("Only new notifications may be published");
        if(n.getUser()==null)n.setUser(access.actor());
        n.setTitle(Rules.text(n.getTitle(),"Notification title",true,255));
        n.setType(Rules.text(n.getType(),"Notification type",true,255));
        n.setMessage(Rules.text(n.getMessage(),"Notification message",true,255));
        n.setCreatedAt(null);n.setIsRead(false);
        notifications.saveAndFlush(n);
        String body="Hello "+n.getUser().getName()+",\n\n"+n.getMessage()+"\n\n"
            +(details==null||details.isBlank()?"Reason: A CRM notification was created by "+actor()+".":details)
            +"\n\nNotification: #"+n.getNotificationId()+"\nType: "+n.getType()+"\nRecorded at: "+n.getCreatedAt()
            +"\n\nOpen Notifications in your CRM to review this event.";
        return queue.enqueue(n.getUser().getEmail(),n.getTitle(),body,n.getUser().getUserId(),dealId);
    }
    public EmailDeliveryResult event(User owner,String type,String title,String message,String details,Long dealId) {
        var n=new Notification();n.setUser(owner);n.setType(type);n.setTitle(shorten(title));n.setMessage(shorten(message));
        return publish(n,details,dealId);
    }
    private String shorten(String value){return value.length()>255?value.substring(0,252)+"...":value;}
    private String actor(){var a=SecurityContextHolder.getContext().getAuthentication();return a==null?"the automated CRM service":a.getName();}
    public record State(String type,Long id,String name,User owner,Long dealId,Map<String,String> fields,int credentials) {}
    private String value(Object value){
        if(value==null || value instanceof String s && s.isBlank())return "Not set";
        if(value instanceof java.math.BigDecimal n)return n.stripTrailingZeros().toPlainString();
        return value.toString();
    }
    private Map<String,String> fields(Object... pairs){var map=new LinkedHashMap<String,String>();for(int i=0;i<pairs.length;i+=2)map.put(pairs[i].toString(),value(pairs[i+1]));return map;}
    private String customer(Customer c){return c==null?null:c.getName()+" (#"+c.getCustomerId()+")";}
    private String owner(User u){return u==null?null:u.getName()+" (#"+u.getUserId()+")";}
    public State snapshot(Object record) {
        if(record instanceof Customer c)return new State("CUSTOMER",c.getCustomerId(),c.getName(),c.getAssignedTo(),null,fields("Name",c.getName(),"Email",c.getEmail(),"Phone",c.getPhone(),"Company",c.getCompany(),"Address",c.getAddress(),"Status",c.getStatus(),"Assigned employee",owner(c.getAssignedTo())),0);
        if(record instanceof Lead l)return new State("LEAD",l.getLeadId(),l.getName(),l.getAssignedTo(),null,fields("Name",l.getName(),"Email",l.getEmail(),"Phone",l.getPhone(),"Company",l.getCompany(),"Source",l.getSource(),"Status",l.getStatus(),"Assigned employee",owner(l.getAssignedTo()),"Converted customer",customer(l.getConvertedCustomer())),0);
        if(record instanceof Deal d)return new State("DEAL",d.getDealId(),d.getTitle(),d.getAssignedTo(),d.getDealId(),fields("Title",d.getTitle(),"Amount (INR)",d.getAmount(),"Stage",d.getStage(),"Closing date",d.getExpectedCloseDate(),"Lost reason",d.getLostReason(),"Customer",customer(d.getCustomer()),"Assigned employee",owner(d.getAssignedTo())),0);
        if(record instanceof Task t)return new State("TASK",t.getTaskId(),t.getTitle(),t.getAssignedTo(),t.getLinkedDealId(),fields("Title",t.getTitle(),"Description",t.getDescription(),"Due date",t.getDueDate(),"Status",t.getStatus(),"Priority",t.getPriority(),"Customer",customer(t.getCustomer()),"Related deal",t.getLinkedDealId(),"Assigned employee",owner(t.getAssignedTo())),0);
        if(record instanceof Interaction i)return new State("INTERACTION",i.getInteractionId(),i.getType()+" with "+Objects.toString(customer(i.getCustomer()),"customer not selected"),i.getUser(),i.getLinkedDealId(),fields("Type",i.getType(),"Notes / reason",i.getNotes(),"Interaction date",i.getInteractionDate(),"Next follow-up",i.getNextFollowup(),"Customer",customer(i.getCustomer()),"Related deal",i.getLinkedDealId(),"Assigned employee",owner(i.getUser())),0);
        if(record instanceof Company c)return new State("COMPANY",c.getCompanyId(),c.getName(),access.actor(),null,fields("Name",c.getName(),"Email",c.getEmail(),"Phone",c.getPhone(),"Address",c.getAddress(),"Status",c.getStatus()),0);
        if(record instanceof User u)return new State("ACCOUNT",u.getUserId(),u.getName(),u,null,fields("Name",u.getName(),"Email",u.getEmail(),"Role",u.getRole(),"Company",u.getCompany()==null?null:u.getCompany().getName(),"Manager",owner(u.getManager()),"Active",u.getActive()),u.getTokenVersion());
        throw new IllegalArgumentException("Unsupported notification record");
    }
    public EmailDeliveryResult created(Object record){var s=snapshot(record);return send(s,"created",s.fields(),false);}
    public EmailDeliveryResult updated(Object record,State before){
        var after=snapshot(record);var changes=new LinkedHashMap<String,String>();
        after.fields().forEach((k,v)->{if(!Objects.equals(before.fields().get(k),v))changes.put(k, before.fields().get(k)+" → "+v);});
        if(before.credentials()!=after.credentials())changes.put("Account security","Existing sign-in sessions were invalidated.");
        if(changes.isEmpty())return null;
        String action=after.type().equals("DEAL")&&changes.containsKey("Stage")?"stage changed":after.type().equals("LEAD")&&"CONVERTED".equals(after.fields().get("Status"))?"converted":"updated";
        var result=send(after,action,changes,false);
        if(before.owner()!=null && after.owner()!=null && !before.owner().getUserId().equals(after.owner().getUserId()))
            event(before.owner(),after.type(),"CRM assignment changed",after.type()+" #"+after.id()+" is no longer assigned to you.","Reason: Assignment changed by "+actor()+". Review your current work list.",after.dealId());
        return result;
    }
    public void deleted(State before){send(before,"deleted",Map.of(),true);}
    private EmailDeliveryResult send(State s,String action,Map<String,String> values,boolean deleted){
        String noun=s.type().charAt(0)+s.type().substring(1).toLowerCase(Locale.ROOT);
        String title=noun+" "+action;
        if(s.type().equals("DEAL") && ("created".equals(action)||"stage changed".equals(action)) && Set.of("WON","LOST").contains(s.fields().get("Stage")))title="Deal "+s.fields().get("Stage");
        if(s.type().equals("TASK") && "COMPLETED".equals(s.fields().get("Status")))title="Task completed";
        String details="Reason: "+noun+" "+action+" by "+actor()+".\nRecord: "+s.name()+" (#"+s.id()+")\n";
        for(var e:values.entrySet())details+=e.getKey()+": "+e.getValue()+"\n";
        // Always include the interaction notes and actual lost reason in contextual emails.
        if(s.type().equals("INTERACTION"))details+="Interaction notes: "+s.fields().get("Notes / reason")+"\n";
        if(s.type().equals("DEAL") && "LOST".equals(s.fields().get("Stage")))details+="Lost reason: "+s.fields().get("Lost reason")+"\n";
        return event(deleted&&s.type().equals("ACCOUNT")?access.actor():s.owner(),s.type(),title+": "+s.name(),title+": "+s.name(),details,deleted?null:s.dealId());
    }
}
