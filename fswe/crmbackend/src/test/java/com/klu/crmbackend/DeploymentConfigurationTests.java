package com.klu.crmbackend;

import com.klu.crmbackend.service.EmailService;
import com.klu.crmbackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("prod")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:deployment_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "PUBLIC_APP_URL=https://crm-demo.vercel.app",
    "MAIL_RECIPIENT_OVERRIDE=demo@test.invalid",
    "app.bootstrap.enabled=true", "app.bootstrap.email=admin@deploy.test",
    "app.bootstrap.password=Deployment-test-only-password",
    "app.mail.enabled=false", "app.jobs.enabled=false"
})
class DeploymentConfigurationTests {
    @Autowired private EmailService email;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private Environment env;

    @Test void productionKeepsExplicitDemoRoutingAndExactFrontendOrigin() {
        assertTrue(email.testRouting());
        assertEquals("demo@test.invalid", email.recipient("fake-customer@test.invalid"));
        assertEquals("https://crm-demo.vercel.app", env.getRequiredProperty("app.cors.origins"));
        assertEquals("", env.getRequiredProperty("spring.config.import"));
        assertEquals("false", env.getRequiredProperty("spring.flyway.baseline-on-migrate"));
    }

    @Test void freshCloudSchemaSupportsFirstAdministrator() {
        assertEquals(1, users.count());
        var admin = users.findByEmail("admin@deploy.test").orElseThrow();
        assertEquals("ADMIN", admin.getRole());
        assertTrue(passwords.matches("Deployment-test-only-password", admin.getPassword()));
    }
}
