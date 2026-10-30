package com.klu.crmbackend;
import org.springframework.boot.SpringApplication;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
public class E2eLauncher {
    public static void main(String[] args) {
        var app=new SpringApplication(CrmbackendApplication.class,BrowserPaymentConfig.class);
        var context=app.run("--server.port="+System.getenv().getOrDefault("CRM_E2E_BACKEND_PORT","8086"),"--app.cors.origins=http://127.0.0.1:"+System.getenv().getOrDefault("CRM_E2E_FRONTEND_PORT","5174"),"--spring.config.import=","--spring.datasource.url=jdbc:h2:mem:crm_browser;MODE=MySQL;DB_CLOSE_DELAY=-1","--spring.datasource.username=sa","--spring.datasource.password=","--spring.flyway.enabled=true","--spring.jpa.hibernate.ddl-auto=validate","--app.mail.enabled=false","--app.jobs.enabled=false","--app.jwt.secret=browser-test-key-with-more-than-32-bytes");
        var companies=context.getBean(CompanyRepository.class);var users=context.getBean(UserRepository.class);var encoder=context.getBean(PasswordEncoder.class);
        var company=new Company();company.setName("Browser Test Company");company=companies.saveAndFlush(company);
        for(String role:new String[]{"ADMIN","MANAGER","SALES"}) {
            var user=new User();user.setName("Browser "+role);user.setEmail(role.toLowerCase()+"@browser.test");user.setPassword(encoder.encode("Browser-test-password"));user.setRole(role);user.setCompany(company);if(role.equals("SALES"))user.setManager(users.findByEmail("manager@browser.test").orElseThrow());users.saveAndFlush(user);
        }
        System.out.println("BROWSER_TEST_READY");
    }
}
