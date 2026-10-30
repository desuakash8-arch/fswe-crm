package com.klu.crmbackend;

import com.klu.crmbackend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

class EmailServiceTests {
    @Test void smtpFailureDoesNotFailCrmOperation() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        EmailService service = new EmailService(provider, new com.klu.crmbackend.service.ResendEmailSender(""));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "crm@test.invalid");
        doThrow(new MailSendException("SMTP unavailable")).when(sender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> service.sendDealWonEmail("sales@test.invalid", "Deal", "100", "Sales"));
        verify(sender).send(any(SimpleMailMessage.class));
    }
    @Test void smtpIsNotRequiredWhenEmailIsDisabled() {
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        assertDoesNotThrow(() -> new EmailService(provider, new com.klu.crmbackend.service.ResendEmailSender("")).sendEmail("sales@test.invalid", "Test", "Test"));
    }

    @Test void wonEmailHasCorrectRecipientSubjectAndBody() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        EmailService service = new EmailService(provider, new com.klu.crmbackend.service.ResendEmailSender(""));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "crm@test.invalid");
        var result = service.sendDealWonEmail("sales@test.invalid", "Example", "INR 100", "Employee");
        assertTrue(result.sent());
        assertTrue(result.message().contains("Inbox delivery is not yet confirmed"));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertArrayEquals(new String[]{"sales@test.invalid"}, message.getValue().getTo());
        assertEquals("crm@test.invalid", message.getValue().getFrom());
        assertEquals("Deal Won: Example", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("INR 100"));
    }

    @Test void localTestOverrideReplacesRecipientWithoutCopyingOriginalAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        EmailService service = new EmailService(provider, new com.klu.crmbackend.service.ResendEmailSender(""));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "crm@test.invalid");
        ReflectionTestUtils.setField(service, "recipientOverride", " test-inbox@test.invalid ");
        var result = service.sendDealWonEmail("user19@crmhub.com", "Example", "INR 100", "Employee");
        assertTrue(result.sent());
        assertTrue(result.message().contains("test-inbox@test.invalid"));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertArrayEquals(new String[]{"test-inbox@test.invalid"}, message.getValue().getTo());
        assertNull(message.getValue().getCc());
        assertNull(message.getValue().getBcc());
        assertEquals("[LOCAL TEST] Deal Won: Example", message.getValue().getSubject());
        assertTrue(message.getValue().getText().contains("Original recipient: user19@crmhub.com"));
    }
}
