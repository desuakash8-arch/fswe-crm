package com.klu.crmbackend.service;

import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Builds small, permission-filtered snapshots. Never serialize entities into AI prompts. */
@Service
@Transactional(readOnly = true)
public class AssistantContextService {
    public enum Mode { DAILY_PRIORITIES, CUSTOMER_SUMMARY, DEAL_HEALTH, EMAIL_DRAFT }
    public record Source(String id, String label, String href) {}
    public record Context(Long actorId, String scope, String generatedAt, List<String> facts,
                          Map<String, Object> data, List<Source> sources) {}
    private final RecordAccessService access;
    private final CustomerRepository customers;
    private final DealRepository deals;
    private final TaskRepository tasks;
    private final InteractionRepository interactions;
    private final DealHealthService health;
    @PersistenceContext private EntityManager em;

    public AssistantContextService(RecordAccessService access, CustomerRepository customers, DealRepository deals,
            TaskRepository tasks, InteractionRepository interactions, DealHealthService health) {
        this.access = access; this.customers = customers; this.deals = deals;
        this.tasks = tasks; this.interactions = interactions; this.health = health;
    }

    public Context build(Mode mode, Long customerId, Long dealId) {
        var actor = access.actor();
        var now = LocalDateTime.now();
        var data = new LinkedHashMap<String, Object>();
        var sources = new LinkedHashMap<String, Source>();
        var facts = new ArrayList<String>();
        String scope = "ADMIN".equals(actor.getRole()) ? "All CRM records you can access"
                : "MANAGER".equals(actor.getRole()) ? "Your records and your company's Sales records" : "Your assigned records";
        data.put("asOf", now.toString());
        data.put("timezone", ZoneId.systemDefault().getId());
        data.put("scope", scope);
        data.put("senderName", text(actor.getName()));
        if (mode == Mode.DAILY_PRIORITIES) {
            if (customerId != null || dealId != null) throw new IllegalArgumentException("Daily priorities do not take a selected record");
            daily(actor, now, data, sources, facts);
        } else if (mode == Mode.DEAL_HEALTH) {
            if (dealId == null) throw new IllegalArgumentException("Select a deal first");
            var deal = permittedDeal(actor, dealId);
            if (customerId != null && (deal.getCustomer() == null || !customerId.equals(deal.getCustomer().getCustomerId())))
                throw new IllegalArgumentException("The selected deal must belong to the selected customer");
            var result = health.one(dealId);
            data.put("deal", dealData(deal, sources));
            data.put("health", result);
            facts.add("Recorded health: " + result.status().replace('_', ' '));
            result.reasons().forEach(reason -> facts.add(reason.message()));
        } else {
            if (customerId == null) throw new IllegalArgumentException("Select a customer first");
            var customer = customers.findById(customerId).filter(c -> access.canAccess(actor, c.getAssignedTo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
            if (dealId != null && mode != Mode.EMAIL_DRAFT) throw new IllegalArgumentException("Choose a customer without a deal for a customer summary");
            if (dealId != null) {
                var deal = permittedDeal(actor, dealId);
                if (deal.getCustomer() == null || !customerId.equals(deal.getCustomer().getCustomerId()))
                    throw new IllegalArgumentException("The selected deal must belong to this customer");
                data.put("selectedDeal", dealData(deal, sources));
                data.put("health", health.one(dealId));
            }
            customer(actor, customer, now, dealId, data, sources, facts);
        }
        return new Context(actor.getUserId(), scope, now.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(), List.copyOf(facts),
                Collections.unmodifiableMap(data), List.copyOf(sources.values()));
    }

    private Deal permittedDeal(User actor, Long id) {
        return deals.findById(id).filter(d -> access.canAccess(actor, d.getAssignedTo()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal not found"));
    }

    private void daily(User actor, LocalDateTime now, Map<String, Object> data,
            Map<String, Source> sources, List<String> facts) {
        var today = now.toLocalDate();
        Specification<Task> due = RecordQuery.<Task>visible(actor, "assignedTo").and((r, q, cb) -> cb.and(
                r.get("status").in("PENDING", "IN_PROGRESS"), cb.lessThanOrEqualTo(r.get("dueDate"), today)));
        long dueCount = tasks.count(due);
        data.put("dueTasks", rows(Task.class, due, "dueDate", false, 10).stream().map(t -> taskData(t, sources)).toList());
        data.put("dueTaskCount", dueCount);
        var followups = dueFollowups(actor, now);
        long followupCount = interactions.count(followups);
        data.put("dueFollowups", rows(Interaction.class, followups, "nextFollowup", false, 8).stream()
                .map(i -> interactionData(i, sources)).toList());
        data.put("dueFollowupCount", followupCount);
        Specification<Deal> open = RecordQuery.<Deal>visible(actor, "assignedTo")
                .and((r, q, cb) -> cb.not(r.get("stage").in("WON", "LOST")));
        var sample = rows(Deal.class, open, "expectedCloseDate", false, 8);
        var evaluated = health.batch(sample.stream().map(d -> Objects.requireNonNull(d).getDealId()).toList());
        data.put("openDeals", sample.stream().map(d -> {
            var item = dealData(d, sources); item.put("health", evaluated.get(d.getDealId())); return item;
        }).toList());
        data.put("openDealCount", deals.count(open));
        data.put("coverage", "Up to 10 oldest due tasks, 8 oldest due follow-ups, and 8 open deals ordered by closing date (missing dates last). This is a bounded snapshot, not a full pipeline review.");
        facts.add(dueCount + " unfinished task(s) due today or earlier");
        facts.add(followupCount + " unconverted follow-up(s) due today or earlier");
        facts.add("Reviewed " + sample.size() + " of " + data.get("openDealCount") + " visible open deals");
    }

    // A linked task's current status is authoritative. Newer completed contact supersedes old follow-ups.
    private Specification<Interaction> dueFollowups(User actor, LocalDateTime now) {
        return RecordQuery.<Interaction>visible(actor, "user").and((r, q, cb) -> {
            var linked = q.subquery(Long.class); var t = linked.from(Task.class);
            linked.select(t.get("taskId")).where(cb.equal(t.get("interaction").get("interactionId"), r.get("interactionId")),
                    RecordQuery.visiblePredicate(t, cb, actor, "assignedTo"));
            var newer = q.subquery(Long.class); var n = newer.from(Interaction.class);
            var sameDeal = cb.and(cb.isNotNull(r.get("deal")), cb.equal(n.get("deal"), r.get("deal")));
            var sameGeneralCustomer = cb.and(cb.isNull(r.get("deal")), cb.isNull(n.get("deal")),
                    cb.isNotNull(r.get("customer")), cb.equal(n.get("customer"), r.get("customer")));
            newer.select(n.get("interactionId")).where(RecordQuery.visiblePredicate(n, cb, actor, "user"),
                    cb.or(sameDeal, sameGeneralCustomer), cb.lessThanOrEqualTo(n.get("interactionDate"), now),
                    cb.or(cb.greaterThan(n.get("interactionDate"), r.get("interactionDate")),
                        cb.and(cb.equal(n.get("interactionDate"), r.get("interactionDate")), cb.greaterThan(n.get("interactionId"), r.get("interactionId")))));
            return cb.and(cb.lessThanOrEqualTo(r.get("interactionDate"), now),
                    cb.lessThan(r.get("nextFollowup"), now.toLocalDate().plusDays(1).atStartOfDay()),
                    cb.not(cb.exists(linked)), cb.not(cb.exists(newer)));
        });
    }

    private void customer(User actor, Customer customer, LocalDateTime now, Long selectedDeal,
            Map<String, Object> data, Map<String, Source> sources, List<String> facts) {
        Long id = customer.getCustomerId();
        String sourceId = "customer-" + id;
        sources.put(sourceId, new Source(sourceId, text(customer.getName()), "/customers/" + id));
        data.put("customer", Map.of("sourceId", sourceId, "name", text(customer.getName()),
                "company", text(customer.getCompany()), "status", text(customer.getStatus())));
        Specification<Deal> relatedDeals = RecordQuery.<Deal>visible(actor, "assignedTo")
                .and((r, q, cb) -> cb.equal(r.get("customer").get("customerId"), id));
        Specification<Interaction> relatedCalls = RecordQuery.<Interaction>visible(actor, "user")
                .and((r, q, cb) -> cb.and(cb.equal(r.get("customer").get("customerId"), id),
                    cb.lessThanOrEqualTo(r.get("interactionDate"), now)));
        Specification<Task> relatedTasks = RecordQuery.<Task>visible(actor, "assignedTo")
                .and((r, q, cb) -> cb.and(cb.equal(r.get("customer").get("customerId"), id), r.get("status").in("PENDING", "IN_PROGRESS")));
        if (selectedDeal != null) {
            relatedDeals = relatedDeals.and((r, q, cb) -> cb.equal(r.get("dealId"), selectedDeal));
            relatedCalls = relatedCalls.and((r, q, cb) -> cb.equal(r.get("deal").get("dealId"), selectedDeal));
            relatedTasks = relatedTasks.and((r, q, cb) -> cb.equal(r.get("deal").get("dealId"), selectedDeal));
        }
        data.put("deals", rows(Deal.class, relatedDeals, "createdAt", true, 8).stream().map(d -> dealData(d, sources)).toList());
        data.put("recentInteractions", rows(Interaction.class, relatedCalls, "interactionDate", true, 8).stream().map(i -> interactionData(i, sources)).toList());
        data.put("openTasks", rows(Task.class, relatedTasks, "dueDate", false, 8).stream().map(t -> taskData(t, sources)).toList());
        data.put("visibleDealCount", deals.count(relatedDeals));
        data.put("visibleInteractionCount", interactions.count(relatedCalls));
        data.put("visibleOpenTaskCount", tasks.count(relatedTasks));
        data.put("coverage", "Up to 8 recent deals, 8 most recent completed interactions, and 8 earliest open tasks. Counts cover all matching visible records. Notes are clipped at 500 characters. Future-dated interactions are excluded.");
        facts.add("Customer: " + text(customer.getName()) + " · " + text(customer.getStatus()));
        facts.add(data.get("visibleDealCount") + " visible deal(s) · " + data.get("visibleInteractionCount") + " completed interaction(s) · " + data.get("visibleOpenTaskCount") + " open task(s)");
        if (selectedDeal != null) facts.add("Draft context is limited to the selected deal");
    }

    private <T> List<T> rows(Class<T> type, Specification<T> spec, String order, boolean desc, int limit) {
        var cb = em.getCriteriaBuilder(); var query = cb.createQuery(type); var root = query.from(type);
        query.where(spec.toPredicate(root, query, cb));
        query.orderBy(cb.asc(cb.selectCase().when(cb.isNull(root.get(order)), 1).otherwise(0)),
                desc ? cb.desc(root.get(order)) : cb.asc(root.get(order)), cb.asc(root.get(type == Deal.class ? "dealId" : type == Task.class ? "taskId" : "interactionId")));
        return em.createQuery(query).setMaxResults(limit).getResultList();
    }

    private Map<String, Object> dealData(Deal deal, Map<String, Source> sources) {
        String id = "deal-" + deal.getDealId();
        sources.put(id, new Source(id, text(deal.getTitle()), "/deals/" + deal.getDealId() + "/health"));
        var result = new LinkedHashMap<String, Object>();
        result.put("sourceId", id); result.put("title", text(deal.getTitle())); result.put("stage", deal.getStage());
        result.put("amountINR", deal.getAmount()); result.put("expectedCloseDate", value(deal.getExpectedCloseDate()));
        result.put("lostReason", text(deal.getLostReason()));
        return result;
    }
    private Map<String, Object> taskData(Task task, Map<String, Source> sources) {
        String id = "task-" + task.getTaskId();
        sources.put(id, new Source(id, text(task.getTitle()), "/tasks"));
        return Map.of("sourceId", id, "title", text(task.getTitle()), "description", text(task.getDescription()),
                "dueDate", value(task.getDueDate()), "status", text(task.getStatus()), "priority", text(task.getPriority()));
    }
    private Map<String, Object> interactionData(Interaction call, Map<String, Source> sources) {
        String id = "interaction-" + call.getInteractionId();
        sources.put(id, new Source(id, text(call.getType()) + " · " + value(call.getInteractionDate()), "/interactions"));
        return Map.of("sourceId", id, "type", text(call.getType()), "notes", text(call.getNotes()),
                "date", value(call.getInteractionDate()), "nextFollowup", value(call.getNextFollowup()));
    }
    private static String value(Object v) { return v == null ? "Not recorded" : v.toString(); }
    private static String text(String value) { return value == null ? "" : value.substring(0, Math.min(500, value.length())); }
}
