
package com.klu.crmbackend.service;

import com.klu.crmbackend.dto.EmailDeliveryResult;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.MailAuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    private final JavaMailSender mailSender;
    private final ResendEmailSender resend;

    @Value("${app.mail.provider:smtp}")
    private String provider = "smtp";

    @Value("${app.mail.from:crm@localhost}")
    private String from;

    @Value("${app.mail.recipient-override:}")
    private String recipientOverride = "";

    public EmailService(org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSender, ResendEmailSender resend) {
        this.mailSender = mailSender.getIfAvailable();
        this.resend = resend;
    }

    public boolean isEnabled() { return enabled; }
    public boolean testRouting() { return recipientOverride!=null && !recipientOverride.isBlank(); }
    public String recipient(String to) { return testRouting()?recipientOverride.trim():to; }
    public EmailDeliveryResult sendEmail(String to,String subject,String body) {
        return sendDirectEmail(recipient(to),testRouting()?"[LOCAL TEST] "+subject:subject,
            testRouting()?"Local test: redirected to the configured test inbox.\nOriginal recipient: "+to+"\n\n"+body:body);
    }
    public EmailDeliveryResult sendDirectEmail(String to, String subject, String body) {
        if (!enabled) {
            log.info("Email skipped because app.mail.enabled is false");
            return new EmailDeliveryResult(false, "Email is disabled in the backend configuration.");
        }
        String recipient = to;
        if (recipient == null || recipient.isBlank()) {
            return new EmailDeliveryResult(false, "The assigned employee has no email address.");
        }
        try {
            new InternetAddress(from, true).validate();
            new InternetAddress(recipient, true).validate();
        } catch (AddressException ex) {
            log.warn("Email skipped because the sender or recipient address is invalid");
            return new EmailDeliveryResult(false, "Check the sender and assigned employee email addresses.");
        }

        if ("resend".equalsIgnoreCase(provider.trim()))
            return resend.send(from, recipient, subject, body);
        if (!"smtp".equalsIgnoreCase(provider.trim()))
            return new EmailDeliveryResult(false, "Unsupported MAIL_PROVIDER. Choose smtp or resend.");
        if (mailSender == null) {
            log.warn("Email is enabled but no SMTP host is configured");
            return new EmailDeliveryResult(false, "No SMTP server is configured.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Email accepted by the SMTP server");
            return new EmailDeliveryResult(true, "Email accepted by the mail server for " + recipient
                    + ". Inbox delivery is not yet confirmed.");
        } catch (MailAuthenticationException ex) {
            log.warn("SMTP authentication failed; check MAIL_USERNAME and MAIL_PASSWORD");
            return new EmailDeliveryResult(false, "Email login failed. Check the SMTP address and app password.");
        } catch (MailException ex) {
            log.warn("Email delivery failed; the CRM change was saved: {}", ex.getClass().getSimpleName());
            return new EmailDeliveryResult(false, "Email could not be sent. Check the SMTP connection and backend log.");
        }
    }

    public EmailDeliveryResult sendDealWonEmail(
            String to,
            String dealName,
            String dealValue,
            String assignedEmployee) {

        String subject = "Deal Won: " + dealName;

        String body = """
                Congratulations!

                A deal has been marked as WON.

                Deal: %s
                Deal Value: %s
                Assigned Employee: %s

                Please review the deal details in the CRM.
                """.formatted(dealName, dealValue, assignedEmployee);

        return sendEmail(to, subject, body);
    }

    public EmailDeliveryResult sendDealLostEmail(
            String to,
            String dealName,
            String dealValue,
            String assignedEmployee,
            String reason) {

        String subject = "Deal Lost: " + dealName;

        String body = """
                A deal has been marked as LOST.

                Deal: %s
                Deal Value: %s
                Assigned Employee: %s
                Reason: %s

                Please review the deal details in the CRM.
                """.formatted(
                    dealName,
                    dealValue,
                    assignedEmployee,
                    reason == null || reason.isBlank()
                            ? "Not provided"
                            : reason
                );

        return sendEmail(to, subject, body);
    }
}
