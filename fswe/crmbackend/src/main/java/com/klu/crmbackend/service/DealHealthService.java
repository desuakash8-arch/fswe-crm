package com.klu.crmbackend.service;

import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class DealHealthService {
    private final DealRepository deals;
    private final InteractionRepository interactions;
    private final TaskRepository tasks;
    private final RecordAccessService access;
    private final int inactiveDays, overdueRiskDays;
    public DealHealthService(DealRepository deals, InteractionRepository interactions, TaskRepository tasks,
            RecordAccessService access, @Value("${app.deal-health.inactive-days:7}") int inactiveDays,
            @Value("${app.deal-health.overdue-risk-days:3}") int overdueRiskDays) {
        if(inactiveDays<1 || overdueRiskDays<1) throw new IllegalArgumentException("Deal health thresholds must be positive");
        this.deals=deals;this.interactions=interactions;this.tasks=tasks;this.access=access;
        this.inactiveDays=inactiveDays;this.overdueRiskDays=overdueRiskDays;
    }
    public record Reason(String code, String severity, String message) {}
    public record Action(String kind, String label, String description) {}
    public record Health(String status, List<Reason> reasons, List<Action> actions,
            LocalDateTime lastInteraction, LocalDateTime nextAction, int overdueTasks,
            LocalDateTime evaluatedAt, int inactiveDays, int overdueRiskDays) {}

    public Health one(Long id) {
        var result=batch(List.of(id)).get(id);
        if(result==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found");
        return result;
    }
    public Map<Long,Health> batch(List<Long> ids) {
        if(ids==null || ids.size()>100 || ids.stream().anyMatch(id->id==null || id<1))
            throw new IllegalArgumentException("Provide up to 100 valid deal IDs");
        if(ids.isEmpty()) return Map.of();
        var actor=access.actor();
        var visible=deals.findAll(RecordQuery.<Deal>visible(actor,"assignedTo").and((r,q,cb)->r.get("dealId").in(ids)));
        if(visible.isEmpty()) return Map.of();
        var allowed=visible.stream().map(d->Objects.requireNonNull(d).getDealId()).toList();
        var calls=interactions.findAll(RecordQuery.<Interaction>visible(actor,"user").and((r,q,cb)->r.get("deal").get("dealId").in(allowed)));
        var work=tasks.findAll(RecordQuery.<Task>visible(actor,"assignedTo").and((r,q,cb)->r.get("deal").get("dealId").in(allowed)));
        var byDealCalls=new HashMap<Long,List<Interaction>>();
        var byDealTasks=new HashMap<Long,List<Task>>();
        for(var i:calls) byDealCalls.computeIfAbsent(i.getDeal().getDealId(),k->new ArrayList<>()).add(i);
        for(var t:work) byDealTasks.computeIfAbsent(t.getDeal().getDealId(),k->new ArrayList<>()).add(t);
        var result=new LinkedHashMap<Long,Health>();var now=LocalDateTime.now();
        for(var deal:visible) result.put(deal.getDealId(),evaluate(deal,byDealCalls.getOrDefault(deal.getDealId(),List.of()),
            byDealTasks.getOrDefault(deal.getDealId(),List.of()),now,inactiveDays,overdueRiskDays));
        return result;
    }
    public static Health evaluate(Deal deal,List<Interaction> calls,List<Task> tasks,LocalDateTime now,int inactiveDays,int overdueRiskDays) {
        if("WON".equals(deal.getStage()) || "LOST".equals(deal.getStage()))
            return new Health("CLOSED",List.of(new Reason("CLOSED","INFO","This deal is "+deal.getStage().toLowerCase()+". Open-deal health checks do not apply.")),List.of(),null,null,0,now,inactiveDays,overdueRiskDays);
        var reasons=new ArrayList<Reason>();var actions=new LinkedHashMap<String,Action>();var today=now.toLocalDate();
        var latest=calls.stream().filter(i->i.getInteractionDate()!=null && !i.getInteractionDate().isAfter(now))
            .max(Comparator.comparing((Interaction i)->Objects.requireNonNull(i).getInteractionDate()).thenComparing(i->i.getInteractionId()==null?0L:i.getInteractionId())).orElse(null);
        LocalDateTime last=latest==null?null:latest.getInteractionDate(),next=null;
        var open=tasks.stream().filter(t->"PENDING".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus())).toList();
        int overdue=0;long maxOverdue=0;
        for(var t:open) {
            if(t.getDueDate()==null) continue;
            if(t.getDueDate().isBefore(today)) {overdue++;maxOverdue=Math.max(maxOverdue,ChronoUnit.DAYS.between(t.getDueDate(),today));}
            else {var date=t.getDueDate().atStartOfDay();if(next==null || date.isBefore(next)) next=date;}
        }
        // Once a follow-up becomes a task, its task status/due date is authoritative.
        boolean hasTask=latest!=null && tasks.stream().anyMatch(t->t.getInteraction()!=null && Objects.equals(t.getInteraction().getInteractionId(),latest.getInteractionId()));
        LocalDateTime followup=latest==null || hasTask?null:latest.getNextFollowup();
        if(followup!=null && !followup.isBefore(now) && (next==null || followup.isBefore(next))) next=followup;
        if(overdue>0) {
            reasons.add(new Reason("OVERDUE_TASK",maxOverdue>=overdueRiskDays?"RISK":"ATTENTION",overdue+" unfinished deal task(s) are overdue; the oldest is "+maxOverdue+" day(s) late."));
            actions.put("TASKS",new Action("TASKS","Review follow-ups","Complete or reschedule the overdue tasks after checking with the customer."));
        }
        if(followup!=null && followup.isBefore(now)) {
            long days=ChronoUnit.DAYS.between(followup,now);
            reasons.add(new Reason("OVERDUE_FOLLOWUP",days>=overdueRiskDays?"RISK":"ATTENTION","The latest interaction's follow-up was due on "+followup.toLocalDate()+" at "+followup.toLocalTime()+"."));
            actions.put("INTERACTIONS",new Action("INTERACTIONS","Review the follow-up","Record the outcome or create a linked follow-up task from this interaction."));
        }
        if(last==null) {
            reasons.add(new Reason("NO_INTERACTION","ATTENTION","No completed interaction is linked to this deal. General customer activity is not counted."));
            actions.put("INTERACTIONS",new Action("INTERACTIONS","Record an interaction","Log the call, email, or meeting for this specific opportunity."));
        } else if(ChronoUnit.DAYS.between(last,now)>=inactiveDays) {
            reasons.add(new Reason("INACTIVE","ATTENTION","The last linked interaction was "+ChronoUnit.DAYS.between(last,now)+" day(s) ago."));
            actions.put("INTERACTIONS",new Action("INTERACTIONS","Contact the customer","Check progress and record the conversation against this deal."));
        }
        if(next==null) {
            reasons.add(new Reason("NO_NEXT_ACTION","ATTENTION","No upcoming task or follow-up is scheduled for this deal."));
            actions.putIfAbsent("TASKS",new Action("TASKS","Schedule a follow-up","Add a dated task so the next action has an owner and a deadline."));
        }
        if(deal.getExpectedCloseDate()==null) {
            reasons.add(new Reason("NO_CLOSE_DATE","ATTENTION","An expected closing date has not been set."));
            actions.put("CLOSE_DATE",new Action("CLOSE_DATE","Set a closing date","Agree on a realistic target date with the customer."));
        } else if(deal.getExpectedCloseDate().isBefore(today)) {
            reasons.add(new Reason("CLOSE_DATE_PASSED","RISK","The expected closing date, "+deal.getExpectedCloseDate()+", has passed while the deal is still open."));
            actions.put("CLOSE_DATE",new Action("CLOSE_DATE","Review the closing date","Confirm the current timeline and update the date. Review the stage separately if the deal has closed."));
        }
        String status=reasons.stream().anyMatch(r->r.severity().equals("RISK"))?"AT_RISK":reasons.isEmpty()?"ON_TRACK":"NEEDS_ATTENTION";
        if(reasons.isEmpty()) reasons.add(new Reason("CURRENT","INFO","Recent deal activity, an upcoming action, and a current closing date are recorded."));
        return new Health(status,List.copyOf(reasons),List.copyOf(actions.values()),last,next,overdue,now,inactiveDays,overdueRiskDays);
    }
}
