package com.klu.crmbackend;
import com.klu.crmbackend.dto.EmailDeliveryResult;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DealEmailTests {
    DealRepository deals=mock(DealRepository.class);NotificationRepository notifications=mock(NotificationRepository.class);
    EmailQueue queue=mock(EmailQueue.class);RecordAccessService access=mock(RecordAccessService.class);AuditService audit=mock(AuditService.class);
    DealService service=new DealService(deals,new CrmNotifications(notifications,queue,access),access,audit,mock(PaymentInvoiceRepository.class));User employee;
    @BeforeEach void setup(){employee=new User();employee.setUserId(10L);employee.setName("Employee");employee.setEmail("sales@test.invalid");when(deals.save(any())).thenAnswer(i->i.getArgument(0));when(deals.saveAndFlush(any())).thenAnswer(i->{Deal d=i.getArgument(0);d.setDealId(1L);return d;});when(access.resolveAssignment(any())).thenReturn(employee);when(queue.enqueue(anyString(),anyString(),anyString(),anyLong(),anyLong())).thenReturn(new EmailDeliveryResult(false,"Email queued."));}
    Deal deal(String stage){var d=new Deal();d.setDealId(1L);d.setTitle("Test Deal");d.setAmount(BigDecimal.TEN);d.setStage(stage);d.setAssignedTo(employee);return d;}
    @Test void winningDealQueuesForExistingAssignee(){when(deals.findById(1L)).thenReturn(Optional.of(deal("PROPOSAL")));var details=deal("WON");details.setAssignedTo(null);var d=service.updateDeal(1L,details);assertSame(employee,d.getAssignedTo());assertNotNull(d.getClosedDate());assertEquals("Email queued.",d.getEmailDelivery().message());verify(queue).enqueue(eq("sales@test.invalid"),contains("WON"),anyString(),eq(10L),eq(1L));verify(access,never()).resolveAssignment(any());}
    @Test void createdWonDealQueues(){assertNotNull(service.createDeal(deal("WON")).getEmailDelivery());verify(queue).enqueue(anyString(),anyString(),anyString(),eq(10L),eq(1L));}
    @Test void resavingWonDealDoesNotQueueAgain(){when(deals.findById(1L)).thenReturn(Optional.of(deal("WON")));assertNull(service.updateDeal(1L,deal("WON")).getEmailDelivery());verifyNoInteractions(queue,notifications);}
    @Test void lostReasonIsRequired(){var d=deal("LOST");assertThrows(IllegalArgumentException.class,()->service.createDeal(d));verifyNoInteractions(queue);}
    @Test void reopeningClearsClosingDateAndNotifies(){var d=deal("WON");d.setClosedDate(java.time.LocalDate.now());when(deals.findById(1L)).thenReturn(Optional.of(d));assertNull(service.updateDeal(1L,deal("LEAD")).getClosedDate());verify(queue).enqueue(eq("sales@test.invalid"),contains("stage changed"),contains("WON → LEAD"),eq(10L),eq(1L));}
}
