package com.klu.crmbackend;

import com.klu.crmbackend.model.*;
import com.klu.crmbackend.service.DealHealthService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DealHealthTests {
    final LocalDateTime now=LocalDateTime.of(2026,10,15,12,0);
    Deal deal() {var d=new Deal();d.setStage("PROPOSAL");d.setExpectedCloseDate(now.toLocalDate().plusDays(5));return d;}
    Interaction call(LocalDateTime date,LocalDateTime next) {var i=new Interaction();i.setInteractionId(1L);i.setInteractionDate(date);i.setNextFollowup(next);return i;}
    Task task(LocalDate date,String status) {var t=new Task();t.setDueDate(date);t.setStatus(status);return t;}
    DealHealthService.Health health(Deal d,List<Interaction> i,List<Task> t) {return DealHealthService.evaluate(d,i,t,now,7,3);}
    @Test void recentContactUpcomingActionAndCurrentDateAreOnTrack() {
        var h=health(deal(),List.of(call(now.minusHours(1),now.plusDays(1))),List.of());
        assertEquals("ON_TRACK",h.status());assertTrue(h.actions().isEmpty());assertEquals(now.plusDays(1),h.nextAction());
    }
    @Test void newOrHistoricalUnlinkedDealsAreNotAssumedHealthy() {
        var d=deal();d.setExpectedCloseDate(null);var h=health(d,List.of(),List.of());
        assertEquals("NEEDS_ATTENTION",h.status());
        assertEquals(Set.of("NO_INTERACTION","NO_NEXT_ACTION","NO_CLOSE_DATE"),new HashSet<>(h.reasons().stream().map(r->Objects.requireNonNull(r).code()).toList()));
    }
    @Test void inactivityThresholdUsesElapsedTimeAndIgnoresFutureInteractions() {
        assertEquals("ON_TRACK",health(deal(),List.of(call(now.minusDays(7).plusMinutes(1),now.plusDays(1))),List.of()).status());
        assertEquals("NEEDS_ATTENTION",health(deal(),List.of(call(now.minusDays(7),now.plusDays(1))),List.of()).status());
        assertNull(health(deal(),List.of(call(now.plusHours(1),now.plusDays(1))),List.of()).lastInteraction());
    }
    @Test void overdueTaskThresholdAndDueTodayAreDifferent() {
        var calls=List.of(call(now.minusHours(1),null));
        assertEquals("ON_TRACK",health(deal(),calls,List.of(task(now.toLocalDate(),"PENDING"))).status());
        assertEquals("NEEDS_ATTENTION",health(deal(),calls,List.of(task(now.toLocalDate().minusDays(2),"IN_PROGRESS"))).status());
        var h=health(deal(),calls,List.of(task(now.toLocalDate().minusDays(3),"PENDING"),task(now.toLocalDate().plusDays(1),"PENDING")));
        assertEquals("AT_RISK",h.status());assertEquals(1,h.overdueTasks());
    }
    @Test void completedOrCancelledFollowupDoesNotStayOverdue() {
        var i=call(now.minusDays(5),now.minusDays(4));
        for(String status:List.of("COMPLETED","CANCELLED")) {
            var t=task(now.toLocalDate().minusDays(4),status);t.setInteraction(i);
            var h=health(deal(),List.of(i),List.of(t,task(now.toLocalDate().plusDays(1),"PENDING")));
            assertEquals("ON_TRACK",h.status());assertEquals(0,h.overdueTasks());
        }
    }
    @Test void latestInteractionSupersedesOldUnconvertedFollowup() {
        var old=call(now.minusDays(6),now.minusDays(4));var recent=call(now.minusHours(2),now.plusDays(2));recent.setInteractionId(2L);
        assertEquals("ON_TRACK",health(deal(),List.of(old,recent),List.of()).status());
        assertEquals("AT_RISK",health(deal(),List.of(old),List.of()).status());
    }
    @Test void pastClosingDateWinsOverHealthyActivityAndClosedDealsSkipChecks() {
        var d=deal();d.setExpectedCloseDate(now.toLocalDate().minusDays(1));
        assertEquals("AT_RISK",health(d,List.of(call(now.minusHours(1),now.plusDays(1))),List.of()).status());
        d.setExpectedCloseDate(now.toLocalDate());
        assertEquals("ON_TRACK",health(d,List.of(call(now.minusHours(1),now.plusDays(1))),List.of()).status());
        for(String stage:List.of("WON","LOST")) {d.setStage(stage);var h=health(d,List.of(),List.of(task(now.toLocalDate().minusDays(20),"PENDING")));assertEquals("CLOSED",h.status());assertTrue(h.actions().isEmpty());}
    }
}
