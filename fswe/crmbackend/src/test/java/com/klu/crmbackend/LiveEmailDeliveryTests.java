package com.klu.crmbackend;

import com.klu.crmbackend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Explicit opt-in only: sends ONE real email to CRM_SMTP_TEST_RECIPIENT. */
@EnabledIfEnvironmentVariable(named = "CRM_SMTP_TEST_RECIPIENT", matches = ".+")
class LiveEmailDeliveryTests {
    @Test void sendsNotificationEmailThroughConfiguredSmtp() throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        Properties defaults = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            defaults.load(input);
        }
        env.getPropertySources().addLast(new PropertiesPropertySource("application", defaults));
        Properties local = new Properties();
        try (var input = Files.newInputStream(Path.of("application-local.properties"))) { local.load(input); }
        env.getPropertySources().addFirst(new PropertiesPropertySource("local", local));

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(env.getRequiredProperty("spring.mail.host"));
        sender.setPort(env.getProperty("spring.mail.port", Integer.class, 587));
        sender.setUsername(env.getRequiredProperty("spring.mail.username"));
        sender.setPassword(env.getRequiredProperty("spring.mail.password"));
        sender.setDefaultEncoding("UTF-8");
        Properties settings = sender.getJavaMailProperties();
        settings.setProperty("mail.smtp.auth", "true");
        settings.setProperty("mail.smtp.starttls.enable", "true");
        settings.setProperty("mail.smtp.starttls.required", "true");
        settings.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        settings.setProperty("mail.smtp.connectiontimeout", "10000");
        settings.setProperty("mail.smtp.timeout", "10000");
        settings.setProperty("mail.smtp.writetimeout", "10000");

        var beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", sender);
        EmailService service = new EmailService(beans.getBeanProvider(JavaMailSender.class), new com.klu.crmbackend.service.ResendEmailSender(""));
        ReflectionTestUtils.setField(service, "enabled", env.getProperty("app.mail.enabled", Boolean.class, false));
        ReflectionTestUtils.setField(service, "from", env.getRequiredProperty("app.mail.from"));
        ReflectionTestUtils.setField(service, "recipientOverride", env.getProperty("app.mail.recipient-override", ""));
        var result = service.sendEmail(System.getenv("CRM_SMTP_TEST_RECIPIENT"),
            "CRM notification email test",
            "Your CRM now queues email for new in-app notifications across customers, leads, deals, interactions, tasks, reminders, companies, accounts and imports.\n\nReason: This is a one-time delivery check after enabling notification emails. No customer, deal or task was changed by this test.\n\nReal notifications include the event, record details and available reasons or notes.");
        assertTrue(result.sent(), result.message());
        System.out.println("Live SMTP test: " + result.message());
    }
}
