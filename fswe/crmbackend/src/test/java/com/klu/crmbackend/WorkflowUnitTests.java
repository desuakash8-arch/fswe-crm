package com.klu.crmbackend;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.service.*;
import com.klu.crmbackend.dto.EmailDeliveryResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WorkflowUnitTests {
    @Test void csvHandlesQuotedCommaNewlineAndEscapedQuotes(){var rows=CsvService.parse("name,email\r\n\"A, B\n\"\"Team\"\"\",a@example.com\r\n");assertEquals(2,rows.size());assertEquals("A, B\n\"Team\"",rows.get(1).get(0));assertThrows(IllegalArgumentException.class,()->CsvService.parse("name\n\"unclosed"));}
    @Test void csvPreventsSpreadsheetFormulaExecution(){assertTrue(CsvService.cell(" =HYPERLINK(\"url\")").startsWith("\"'"));assertEquals("\"Normal\"",CsvService.cell("Normal"));}
    @Test void queueFailureRetriesThenStops(){var jobs=mock(EmailJobRepository.class);var sender=mock(EmailService.class);var job=new EmailJob();job.recipient="test@example.com";job.subject="Test";job.body="Test";when(jobs.ready(any(),any())).thenReturn(List.of(job));when(sender.sendDirectEmail(anyString(),anyString(),anyString())).thenReturn(new EmailDeliveryResult(false,"SMTP unavailable"));var queue=new EmailQueue(jobs,sender);queue.process();assertEquals("PENDING",job.state);queue.process();queue.process();assertEquals("FAILED",job.state);assertEquals(3,job.attempts);assertNull(job.acceptedAt);}
    @Test void acceptedMessageIsNotMarkedDelivered(){var jobs=mock(EmailJobRepository.class);var sender=mock(EmailService.class);var job=new EmailJob();job.recipient="test@example.com";job.subject="Test";job.body="Test";when(jobs.ready(any(),any())).thenReturn(List.of(job));when(sender.sendDirectEmail(anyString(),anyString(),anyString())).thenReturn(new EmailDeliveryResult(true,"Accepted"));new EmailQueue(jobs,sender).process();assertEquals("ACCEPTED",job.state);assertNotNull(job.acceptedAt);}

    @Test void loginRequestsAreThrottledWithoutBlockingOtherClients()throws Exception {
        var limiter=new com.klu.crmbackend.config.AuthRateLimit();
        for(int i=0;i<11;i++) {var req=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/auth/login");req.setRemoteAddr("192.0.2.1");var response=new org.springframework.mock.web.MockHttpServletResponse();limiter.doFilter(req,response,new org.springframework.mock.web.MockFilterChain());assertEquals(i<10?200:429,response.getStatus());}
        var req=new org.springframework.mock.web.MockHttpServletRequest("POST","/api/auth/login");req.setRemoteAddr("192.0.2.2");var response=new org.springframework.mock.web.MockHttpServletResponse();limiter.doFilter(req,response,new org.springframework.mock.web.MockFilterChain());assertEquals(200,response.getStatus());
    }
}
