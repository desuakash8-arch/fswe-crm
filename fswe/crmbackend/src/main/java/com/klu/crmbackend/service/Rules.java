package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import jakarta.mail.internet.InternetAddress;
import java.util.*;
public final class Rules {
    private Rules() {}
    public static String text(String value,String label,boolean required,int max) {
        if(value==null || value.isBlank()) { if(required) throw new IllegalArgumentException(label+" is required"); return value; }
        if(value.length()>max) throw new IllegalArgumentException(label+" must be at most "+max+" characters");
        return value.trim();
    }
    public static String email(String value) {
        if(value==null || value.isBlank()) return null;
        value=text(value,"Email",false,254).toLowerCase(Locale.ROOT);
        try { new InternetAddress(value,true).validate(); if(!value.contains("@")) throw new IllegalArgumentException(); }
        catch(Exception e) { throw new IllegalArgumentException("Enter a valid email address"); } return value;
    }
    public static String choice(String value,String fallback,String... allowed) {
        String v=value==null || value.isBlank()?fallback:value.trim().toUpperCase(Locale.ROOT);
        if(!List.of(allowed).contains(v)) throw new IllegalArgumentException("Allowed values: "+String.join(", ",allowed)); return v;
    }
    public static void customer(Customer c) { c.setName(text(c.getName(),"Name",true,255));c.setEmail(email(c.getEmail()));c.setPhone(text(c.getPhone(),"Phone",false,40));c.setCompany(text(c.getCompany(),"Company",false,255));c.setAddress(text(c.getAddress(),"Address",false,255));c.setStatus(choice(c.getStatus(),"ACTIVE","ACTIVE","INACTIVE")); }
    public static void lead(Lead l) { l.setName(text(l.getName(),"Name",true,255));l.setEmail(email(l.getEmail()));l.setPhone(text(l.getPhone(),"Phone",false,40));l.setCompany(text(l.getCompany(),"Company",false,255));l.setSource(text(l.getSource(),"Source",false,255));l.setStatus(choice(l.getStatus(),"NEW","NEW","CONTACTED","QUALIFIED","CONVERTED","LOST")); }
    public static void deal(Deal d) { d.setTitle(text(d.getTitle(),"Deal title",true,255)); if(d.getAmount()==null || d.getAmount().signum()<0 || d.getAmount().precision()>16 || d.getAmount().scale()>2) throw new IllegalArgumentException("Amount must be nonnegative, at most 14 whole digits and 2 decimal places");d.setStage(choice(d.getStage(),"LEAD","LEAD","PROPOSAL","NEGOTIATION","WON","LOST"));d.setLostReason(text(d.getLostReason(),"Lost reason","LOST".equals(d.getStage()),1000)); }
    public static void task(Task t) { t.setTitle(text(t.getTitle(),"Task title",true,255));t.setDescription(text(t.getDescription(),"Description",false,255));t.setStatus(choice(t.getStatus(),"PENDING","PENDING","IN_PROGRESS","COMPLETED","CANCELLED"));t.setPriority(choice(t.getPriority(),"MEDIUM","LOW","MEDIUM","HIGH")); }
    public static void interaction(Interaction i) { i.setType(choice(i.getType(),"CALL","CALL","EMAIL","MEETING","NOTE","OTHER"));i.setNotes(text(i.getNotes(),"Notes",false,255)); if(i.getInteractionDate()==null) i.setInteractionDate(java.time.LocalDateTime.now()); if(i.getNextFollowup()!=null && i.getNextFollowup().isBefore(i.getInteractionDate())) throw new IllegalArgumentException("Follow-up must not be before the interaction"); }
    public static void password(String p) { if(p==null || p.length()<10 || p.length()>72) throw new IllegalArgumentException("Use a password between 10 and 72 characters"); }
}
