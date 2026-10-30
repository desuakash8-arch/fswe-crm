package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class RecordQuery {
    @PersistenceContext private EntityManager em;
    private final RecordAccessService access;
    public RecordQuery(RecordAccessService access) { this.access=access; }
    public static <T> Specification<T> visible(User actor, String ownerField) {
        return (root,q,cb) -> visiblePredicate(root,cb,actor,ownerField);
    }
    public static Predicate visiblePredicate(Root<?> root,CriteriaBuilder cb,User actor,String ownerField) {
        if ("ADMIN".equalsIgnoreCase(actor.getRole())) return cb.conjunction();
        var owner=root.join(ownerField, JoinType.LEFT);
        var own=cb.equal(owner.get("userId"),actor.getUserId());
        if (!"MANAGER".equalsIgnoreCase(actor.getRole()) || actor.getCompany()==null) return own;
        return cb.or(own, cb.and(cb.equal(owner.get("company").get("companyId"),actor.getCompany().getCompanyId()),cb.equal(owner.get("role"),"SALES")));
    }
    public record Kind(Class<?> type,String id,String owner,List<String> search,List<String> sorts,String status) {}
    public Kind kind(String key) {
        return switch(key) {
            case "customers" -> new Kind(Customer.class,"customerId","assignedTo",List.of("name","email","phone","company"),List.of("name","createdAt","customerId"),"status");
            case "leads" -> new Kind(Lead.class,"leadId","assignedTo",List.of("name","email","phone","company","source"),List.of("name","createdAt","leadId"),"status");
            case "deals" -> new Kind(Deal.class,"dealId","assignedTo",List.of("title"),List.of("title","amount","expectedCloseDate","createdAt","dealId"),"stage");
            case "tasks" -> new Kind(Task.class,"taskId","assignedTo",List.of("title","description"),List.of("title","dueDate","taskId"),"status");
            case "interactions" -> new Kind(Interaction.class,"interactionId","user",List.of("notes","type"),List.of("interactionDate","interactionId"),"type");
            default -> throw new IllegalArgumentException("Unknown record type");
        };
    }
    private Predicate filter(Root<?> r,CriteriaBuilder cb,Kind k,String search,String status,Long customerId,Long dealId) {
        var predicates=new ArrayList<Predicate>();
        predicates.add(visiblePredicate(r,cb,access.actor(),k.owner()));
        if(search!=null && !search.isBlank()) {
            String value="%"+search.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            predicates.add(cb.or(k.search().stream().map(f->cb.like(cb.lower(r.get(f)),value,'\\')).toArray(Predicate[]::new)));
        }
        if(status!=null && !status.isBlank() && !status.equals("ALL")) predicates.add(cb.equal(r.get(k.status()),status));
        if(customerId!=null) predicates.add(cb.equal(r.get("customer").get("customerId"),customerId));
        if(dealId!=null) predicates.add(cb.equal(r.get("deal").get("dealId"),dealId));
        return cb.and(predicates.toArray(Predicate[]::new));
    }
    public record PageResult(List<?> content,long totalElements,int page,int size,int totalPages) {}
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public PageResult page(String key,int page,int size,String search,String status,String sort,boolean desc,Long customerId) {
        return page(key,page,size,search,status,sort,desc,customerId,null);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public PageResult page(String key,int page,int size,String search,String status,String sort,boolean desc,Long customerId,Long dealId) {
        if(dealId!=null) {
            if(!List.of("tasks","interactions").contains(key)) throw new IllegalArgumentException("Deal filtering supports tasks and interactions");
            var deal=em.find(Deal.class,dealId);
            if(deal==null || !access.canAccess(deal.getAssignedTo())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Deal not found");
        }
        if(page<0 || page>100000 || size<1 || size>100) throw new IllegalArgumentException("Page must be nonnegative; size must be 1 to 100");
        Kind k=kind(key); if(customerId!=null && !List.of("deals","tasks","interactions").contains(key))throw new IllegalArgumentException("Customer filter is not supported for this record type"); String order=k.sorts().contains(sort)?sort:k.id();
        var cb=em.getCriteriaBuilder(); var q=cb.createQuery(k.type()); var r=q.from(k.type());
        q.where(filter(r,cb,k,search,status,customerId,dealId));
        q.orderBy(desc?cb.desc(r.get(order)):cb.asc(r.get(order)),cb.asc(r.get(k.id())));
        var rows=em.createQuery(q).setFirstResult(page*size).setMaxResults(size).getResultList();
        var count=cb.createQuery(Long.class);var cr=count.from(k.type());count.select(cb.count(cr)).where(filter(cr,cb,k,search,status,customerId,dealId));
        long total=em.createQuery(count).getSingleResult();
        return new PageResult(rows,total,page,size,(int)((total+size-1)/size));
    }
}
