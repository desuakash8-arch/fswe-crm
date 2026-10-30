package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
@Service @Transactional(readOnly=true)
public class ReportService {
    @PersistenceContext private EntityManager em;
    private final RecordAccessService access;
    public ReportService(RecordAccessService access) {this.access=access;}
    private Predicate scope(Root<?> r,CriteriaBuilder cb){return RecordQuery.visiblePredicate(r,cb,access.actor(),"assignedTo");}
    private long count(Class<?> type,String field,LocalDate from,LocalDate to,boolean converted) {
        var cb=em.getCriteriaBuilder();var q=cb.createQuery(Long.class);var r=q.from(type);
        var p=new ArrayList<Predicate>();p.add(scope(r,cb));
        if(field!=null) p.add(cb.and(cb.greaterThanOrEqualTo(r.get(field),from.atStartOfDay()),cb.lessThan(r.get(field),to.plusDays(1).atStartOfDay())));
        if(converted)p.add(cb.equal(r.get("status"),"CONVERTED"));
        q.select(cb.count(r)).where(p.toArray(Predicate[]::new));return em.createQuery(q).getSingleResult();
    }
    public Map<String,Object> summary(LocalDate from,LocalDate to) {
        if(to.isBefore(from))throw new IllegalArgumentException("End date must be on or after start date");
        var cb=em.getCriteriaBuilder();var q=cb.createQuery(Object[].class);var d=q.from(Deal.class);
        q.select(cb.array(d.get("stage"),cb.count(d),cb.sum(d.<BigDecimal>get("amount")))).where(scope(d,cb)).groupBy(d.get("stage"));
        var stages=new ArrayList<Map<String,Object>>();BigDecimal open=BigDecimal.ZERO;
        for(var row:em.createQuery(q).getResultList()){String stage=Objects.toString(row[0],"UNKNOWN");BigDecimal amount=row[2]==null?BigDecimal.ZERO:(BigDecimal)row[2];stages.add(Map.of("stage",stage,"count",row[1],"amount",amount));if(!List.of("WON","LOST").contains(stage))open=open.add(amount);}
        var c=cb.createQuery(Object[].class);var cr=c.from(Deal.class);
        c.select(cb.array(cr.get("stage"),cb.count(cr),cb.sum(cr.<BigDecimal>get("amount")))).where(scope(cr,cb),cb.between(cr.get("closedDate"),from,to),cr.get("stage").in("WON","LOST")).groupBy(cr.get("stage"));
        long won=0,lost=0;BigDecimal revenue=BigDecimal.ZERO;
        for(var row:em.createQuery(c).getResultList())if("WON".equals(row[0])){won=(Long)row[1];revenue=(BigDecimal)row[2];}else lost=(Long)row[1];
        var employeeQuery=cb.createQuery(Object[].class);var e=employeeQuery.from(Deal.class);var owner=e.join("assignedTo",JoinType.LEFT);
        employeeQuery.select(cb.array(owner.get("userId"),owner.get("name"),cb.count(e),cb.sum(e.<BigDecimal>get("amount"))))
            .where(scope(e,cb),cb.equal(e.get("stage"),"WON"),cb.between(e.get("closedDate"),from,to)).groupBy(owner.get("userId"),owner.get("name"));
        var employees=em.createQuery(employeeQuery).getResultList().stream().map(row->Map.of("name",Objects.toString(row[1],"Unassigned"),"won",row[2],"amount",row[3]==null?BigDecimal.ZERO:row[3])).toList();
        var monthlyQuery=cb.createQuery(Object[].class);var m=monthlyQuery.from(Deal.class);
        var year=cb.function("year",Integer.class,m.get("closedDate"));var month=cb.function("month",Integer.class,m.get("closedDate"));
        monthlyQuery.select(cb.array(year,month,cb.sum(m.<BigDecimal>get("amount")))).where(scope(m,cb),cb.equal(m.get("stage"),"WON"),cb.between(m.get("closedDate"),from,to)).groupBy(year,month).orderBy(cb.asc(year),cb.asc(month));
        var monthly=em.createQuery(monthlyQuery).getResultList().stream().map(row->Map.of("month",String.format("%s-%02d",row[0],row[1]),"amount",row[2])).toList();
        long leads=count(Lead.class,"createdAt",from,to,false),converted=count(Lead.class,"createdAt",from,to,true);
        var taskQuery=cb.createQuery(Long.class);var t=taskQuery.from(Task.class);taskQuery.select(cb.count(t)).where(scope(t,cb),t.get("status").in("PENDING","IN_PROGRESS"),cb.lessThan(t.get("dueDate"),LocalDate.now()));
        var result=new LinkedHashMap<String,Object>();result.put("from",from);result.put("to",to);result.put("openPipeline",open);result.put("wonValue",revenue);result.put("wonDeals",won);result.put("lostDeals",lost);
        result.put("winRate",won+lost==0?0:100.0*won/(won+lost));result.put("leads",leads);result.put("convertedLeads",converted);result.put("conversionRate",leads==0?0:100.0*converted/leads);
        result.put("customers",count(Customer.class,null,from,to,false));result.put("overdueTasks",em.createQuery(taskQuery).getSingleResult());result.put("stages",stages);result.put("employees",employees);result.put("monthly",monthly);
        return result;
    }
}
