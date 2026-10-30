package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
@Service @Transactional
public class DealService {
    private final PaymentInvoiceRepository invoices;
    private final DealRepository deals;
    private final CrmNotifications events;private final RecordAccessService access;private final AuditService audit;
    @PersistenceContext private EntityManager em;
    public DealService(DealRepository deals,CrmNotifications events,RecordAccessService access,AuditService audit, PaymentInvoiceRepository invoices) {
        this.invoices=invoices;
        this.deals=deals;this.events=events;this.access=access;this.audit=audit;
    }
    public List<Deal> getAllDeals() {return deals.findAll(RecordQuery.visible(access.actor(),"assignedTo"));}
    public Optional<Deal> getDealById(Long id) {return deals.findById(id).filter(d->access.canAccess(d.getAssignedTo()));}
    public Deal createDeal(Deal d) {
        Rules.deal(d); d.setDealId(null);d.setAssignedTo(access.resolveAssignment(d.getAssignedTo()));d.setCustomer(access.resolveCustomer(d.getCustomer()));
        d.setClosedDate(terminal(d.getStage())?LocalDate.now():null);deals.saveAndFlush(d);
        audit.record("DEAL",d.getDealId(),"CREATED","Created in "+d.getStage());d.setEmailDelivery(events.created(d));return d;
    }
    public Deal updateDeal(Long id,Deal details) {
        Deal d=deals.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found"));
        access.requireAccess(d.getAssignedTo());if(em!=null) em.refresh(d,LockModeType.PESSIMISTIC_WRITE);access.requireAccess(d.getAssignedTo());Rules.deal(details);
        if(details.getCustomer()!=null && d.getCustomer()!=null && !Objects.equals(details.getCustomer().getCustomerId(),d.getCustomer().getCustomerId())) {
            long linked=em.createQuery("select count(i) from Interaction i where i.deal.dealId=:id",Long.class).setParameter("id",id).getSingleResult()
                +em.createQuery("select count(t) from Task t where t.deal.dealId=:id",Long.class).setParameter("id",id).getSingleResult();
            if(linked>0) throw new IllegalArgumentException("A deal with linked activity must keep its customer");
        }
        var before=events.snapshot(d);String old=d.getStage();
        if(invoices.existsByDealDealId(id) && (!Objects.equals(d.getStage(),details.getStage()) || d.getAmount().compareTo(details.getAmount())!=0 || details.getCustomer()!=null && !Objects.equals(details.getCustomer().getCustomerId(),d.getCustomer().getCustomerId())))
            throw new IllegalArgumentException("This deal has an issued invoice. Its amount, customer and stage are locked to preserve payment records.");
        d.setTitle(details.getTitle());d.setAmount(details.getAmount());d.setExpectedCloseDate(details.getExpectedCloseDate());d.setLostReason(details.getLostReason());d.setStage(details.getStage());
        if(details.getAssignedTo()!=null)d.setAssignedTo(access.resolveAssignment(details.getAssignedTo()));
        if(details.getCustomer()!=null)d.setCustomer(access.resolveCustomer(details.getCustomer()));
        boolean changed=!Objects.equals(old,d.getStage());
        if(changed) d.setClosedDate(terminal(d.getStage())?LocalDate.now():null);
        deals.save(d);d.setEmailDelivery(null);
        audit.record("DEAL",id,changed?"STAGE_CHANGED":"UPDATED",changed?old+" → "+d.getStage()+("LOST".equals(d.getStage())?"; reason: "+d.getLostReason():""):"Deal details updated");
        d.setEmailDelivery(events.updated(d,before));return d;
    }
    public Deal changeStage(Long id,String stage,String reason) {
        var d=getDealById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found"));
        var details=new Deal();details.setTitle(d.getTitle());details.setAmount(d.getAmount());details.setExpectedCloseDate(d.getExpectedCloseDate());details.setStage(stage);details.setLostReason(reason);
        return updateDeal(id,details);
    }
    private boolean terminal(String stage) {return "WON".equals(stage)||"LOST".equals(stage);}
    public Deal changeExpectedCloseDate(Long id,LocalDate date) {
        var deal=getDealById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found"));
        em.refresh(deal,LockModeType.PESSIMISTIC_WRITE);access.requireAccess(deal.getAssignedTo());
        var before=events.snapshot(deal);deal.setExpectedCloseDate(date);
        audit.record("DEAL",id,"CLOSING_DATE_UPDATED",date==null?"Expected closing date cleared":"Expected closing date: "+date);
        deals.save(deal);events.updated(deal,before);return deal;
    }
    public void deleteDeal(Long id) {
        var d=deals.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deal not found"));access.requireAccess(d.getAssignedTo());
        var before=events.snapshot(d);audit.record("DEAL",id,"DELETED",d.getTitle());deals.delete(d);events.deleted(before);
    }
}
