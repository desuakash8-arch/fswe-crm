package com.klu.crmbackend;

import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.service.*;
import com.klu.crmbackend.dto.EmailDeliveryResult;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:notification_email_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationEmailIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users; @Autowired CompanyRepository companies;
    @Autowired NotificationRepository notifications; @Autowired EmailJobRepository jobs;
    @Autowired TaskRepository tasks; @Autowired ReminderService reminders;
    @Autowired PasswordEncoder encoder; @Autowired JwtService jwt;
    @Autowired CrmNotifications publisher; @Autowired PlatformTransactionManager transactions;
    @Autowired EmailQueue queue;
    @MockitoBean EmailService sender;
    final HttpClient http=HttpClient.newHttpClient();final JsonMapper json=JsonMapper.builder().build();
    User admin,sales,other;Company company;
    record Response(int status,JsonNode body,String raw){}
    Response request(String method,String path,User actor,Object data)throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type",data instanceof String?"text/plain":"application/json").timeout(Duration.ofSeconds(20));
        if(actor!=null)b.header("Authorization","Bearer "+jwt.generateToken(actor.getUserId(),actor.getEmail(),actor.getRole()));
        var r=http.send(b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(data instanceof String s?s:json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
        return new Response(r.statusCode(),r.body().isBlank()?null:json.readTree(r.body()),r.body());
    }
    JsonNode mutation(String method,String path,User actor,Object data,int count)throws Exception {
        long mail=jobs.count(),inapp=notifications.count();var r=request(method,path,actor,data);
        assertTrue(r.status>=200&&r.status<300,r.raw);assertEquals(mail+count,jobs.count(),method+" "+path+" emails");assertEquals(inapp+count,notifications.count(),method+" "+path+" notifications");return r.body;
    }
    EmailJob latest(){return jobs.findAll().stream().max(Comparator.comparing(j->j.id)).orElseThrow();}
    User fixture(String name,String role){var u=new User();u.setName(name);u.setEmail(name+"@email.test");u.setPassword(encoder.encode("Test-password-123"));u.setRole(role);u.setCompany(company);return users.saveAndFlush(u);}
    @BeforeAll void fixtures(){company=new Company();company.setName("Email test company");company=companies.saveAndFlush(company);admin=fixture("admin","ADMIN");sales=fixture("sales","SALES");other=fixture("other","SALES");}
    @BeforeEach void configureSender(){when(sender.isEnabled()).thenReturn(true);when(sender.recipient(anyString())).thenAnswer(i->i.getArgument(0));when(sender.testRouting()).thenReturn(false);}

    @Test void customerLeadDealInteractionTaskAndConversionEventsAreEmailedOnce()throws Exception {
        var customer=mutation("POST","/customers",sales,Map.of("name","Notification customer","status","ACTIVE"),1);long cid=customer.get("customerId").asLong();
        assertEquals(sales.getEmail(),latest().recipient);assertTrue(latest().body.contains("Reason:"));assertTrue(latest().body.contains(sales.getEmail()));
        var lead=mutation("POST","/leads",sales,Map.of("name","Convertible email lead","status","NEW"),1);
        mutation("POST","/leads/"+lead.get("leadId").asLong()+"/convert",sales,Map.of("createDeal",true,"title","Converted opportunity","amount",200),3);
        var dealData=new HashMap<String,Object>();dealData.put("title","Email opportunity");dealData.put("amount",100);dealData.put("stage","LEAD");dealData.put("customer",Map.of("customerId",cid));
        var deal=mutation("POST","/deals",sales,dealData,1);long did=deal.get("dealId").asLong();
        mutation("PUT","/deals/"+did,sales,dealData,0); // Database decimal scale must not create a false change.
        mutation("PATCH","/deals/"+did+"/stage",sales,Map.of("stage","PROPOSAL"),1);assertTrue(latest().body.contains("LEAD → PROPOSAL"));
        mutation("PATCH","/deals/"+did+"/stage",sales,Map.of("stage","LOST","lostReason","Customer chose another supplier"),1);
        assertTrue(latest().body.contains("Customer chose another supplier"));assertEquals(did,latest().dealId);
        mutation("PATCH","/deals/"+did+"/stage",sales,Map.of("stage","LOST","lostReason","Customer chose another supplier"),0);
        mutation("PATCH","/deals/"+did+"/expected-close",sales,Map.of("expectedCloseDate",LocalDate.now().plusDays(5).toString()),1);
        var call=mutation("POST","/interactions",sales,Map.of("notes","Customer requested a pricing review. "+"A".repeat(200),"type","CALL","customer",Map.of("customerId",cid),"deal",Map.of("dealId",did),"interactionDate",LocalDateTime.now().minusHours(1).toString(),"nextFollowup",LocalDateTime.now().plusDays(1).toString()),1);
        assertTrue(latest().body.contains("Customer requested a pricing review"));
        var followup=mutation("POST","/interactions/"+call.get("interactionId").asLong()+"/create-task",sales,null,1);assertTrue(latest().body.contains("Customer requested a pricing review"));
        mutation("PUT","/tasks/"+followup.get("taskId").asLong(),sales,Map.of("title",followup.get("title").asString(),"status","COMPLETED","priority","HIGH","dueDate",LocalDate.now().plusDays(1).toString()),1);
        assertTrue(latest().subject.contains("Task completed"));
        long nid=notifications.findAll().stream().mapToLong(n->Objects.requireNonNull(n).getNotificationId()).max().orElseThrow();
        mutation("PUT","/notifications/"+nid,sales,Map.of("title","Read notification","message","Read notification","type","GENERAL","isRead",true),0);
        mutation("POST","/notifications",sales,Map.of("title","Custom CRM update","message","Please review the quote","type","GENERAL"),1);assertTrue(latest().body.contains("Please review the quote"));
        long n=jobs.count();var denied=request("PATCH","/deals/"+did+"/stage",other,Map.of("stage","WON"));assertEquals(404,denied.status);assertEquals(n,jobs.count());
    }
    @Test void reassignmentUpdatesAndDeletesNotifyCorrectRecipients()throws Exception {
        var c=mutation("POST","/customers",sales,Map.of("name","Reassign customer","status","ACTIVE"),1);long id=c.get("customerId").asLong();
        mutation("PUT","/customers/"+id,admin,Map.of("name","Reassign customer","status","ACTIVE","assignedTo",Map.of("userId",other.getUserId())),2);
        assertEquals(sales.getEmail(),latest().recipient);assertTrue(latest().body.contains("no longer assigned"));
        mutation("PUT","/customers/"+id,other,Map.of("name","Reassign customer","status","ACTIVE"),0);
        mutation("DELETE","/customers/"+id,admin,null,1);assertEquals(other.getEmail(),latest().recipient);assertTrue(latest().subject.contains("deleted"));
    }
    @Test void companiesAccountsAndImportsAreCoveredWithoutEmailingSecrets()throws Exception {
        var c=mutation("POST","/companies",admin,Map.of("name","New email company"),1);long cid=c.get("companyId").asLong();
        mutation("PUT","/companies/"+cid,admin,Map.of("name","Renamed email company","status","ACTIVE"),1);
        var manager=mutation("POST","/users",admin,Map.of("name","Email manager","email","manager-new@email.test","password","Private-password-123","role","MANAGER","companyId",cid),1);
        long uid=manager.get("userId").asLong();assertEquals("manager-new@email.test",latest().recipient);assertFalse(latest().body.contains("Private-password-123"));assertFalse(latest().body.contains("$2"));
        mutation("PATCH","/users/"+uid+"/active",admin,Map.of("active",false),1);assertTrue(latest().body.contains("true → false"));
        mutation("PATCH","/users/"+uid+"/active",admin,Map.of("active",false),0);
        mutation("PUT","/users/"+uid,admin,Map.of("password","New-private-password-123"),1);assertFalse(latest().body.contains("New-private-password-123"));
        // Preview does not write notifications; committing sends record and import-summary notifications.
        var csv="name,email\nImported email lead,imported@email.test\n";
        mutation("POST","/data/leads/import",sales,csv,0);
        mutation("POST","/data/leads/import?commit=true",sales,csv,2);
        assertTrue(latest().body.contains("Imported: 1"));
    }
    @Test void remindersQueueEmailOnceAndRollbackLeavesNeitherNotificationNorEmail() {
        var task=new Task();task.setTitle("Email reminder");task.setAssignedTo(sales);task.setDueDate(LocalDate.now().minusDays(1));task.setStatus("PENDING");task.setPriority("HIGH");task=tasks.saveAndFlush(task);
        long m=jobs.count(),n=notifications.count();reminders.run();assertEquals(m+1,jobs.count());assertEquals(n+1,notifications.count());assertTrue(latest().body.contains("overdue"));
        reminders.run();assertEquals(m+1,jobs.count());
        m=jobs.count();n=notifications.count();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{publisher.event(sales,"GENERAL","Rolled back","Should not be sent","Reason: transaction test",null);tx.setRollbackOnly();});
        assertEquals(m,jobs.count());assertEquals(n,notifications.count());
        task.setStatus("COMPLETED");tasks.saveAndFlush(task);
    }
    @Test void localOverrideAndMailWorkerUseTheExistingQueue() {
        when(sender.testRouting()).thenReturn(true);when(sender.recipient(anyString())).thenReturn("local-test@email.test");
        publisher.event(sales,"GENERAL","Local routing check","Notification body","Reason: local routing test",null);
        var job=latest();assertEquals("local-test@email.test",job.recipient);assertTrue(job.subject.startsWith("[LOCAL TEST]"));assertTrue(job.body.contains("Original recipient: "+sales.getEmail()));
        when(sender.sendDirectEmail(anyString(),anyString(),anyString())).thenReturn(new EmailDeliveryResult(true,"Accepted"));
        while(jobs.findAll().stream().anyMatch(j->j.state.equals("PENDING")))queue.process();
        assertEquals("ACCEPTED",jobs.findById(job.id).orElseThrow().state);
    }
    @Test void recordEditsAndDeletesAndFailedBusinessOperationsHaveMatchingEmailBehavior()throws Exception {
        var c=mutation("POST","/customers",sales,Map.of("name","Detailed event customer","status","ACTIVE"),1);long cid=c.get("customerId").asLong();
        var interaction=new HashMap<String,Object>();interaction.put("type","EMAIL");interaction.put("notes","Initial note");interaction.put("customer",Map.of("customerId",cid));interaction.put("interactionDate",LocalDateTime.now().minusMinutes(1).withNano(0).toString());
        var i=mutation("POST","/interactions",sales,interaction,1);long iid=i.get("interactionId").asLong();interaction.put("notes","Customer approved the revised scope");
        mutation("PUT","/interactions/"+iid,sales,interaction,1);assertTrue(latest().body.contains("Initial note → Customer approved the revised scope"));
        mutation("DELETE","/interactions/"+iid,sales,null,1);
        var t=mutation("POST","/tasks",sales,Map.of("title","Direct task email","dueDate",LocalDate.now().plusDays(2).toString()),1);
        mutation("DELETE","/tasks/"+t.get("taskId").asLong(),sales,null,1);
        var l=mutation("POST","/leads",sales,Map.of("name","Lead email changes","status","NEW"),1);long lid=l.get("leadId").asLong();
        mutation("PUT","/leads/"+lid,sales,Map.of("name","Lead email changes","status","QUALIFIED"),1);
        long mail=jobs.count(),inapp=notifications.count();var failed=request("POST","/leads/"+lid+"/convert",sales,Map.of("createDeal",true,"amount",-1));assertEquals(400,failed.status);assertEquals(mail,jobs.count());assertEquals(inapp,notifications.count());
        mutation("DELETE","/leads/"+lid,admin,null,1);
        var d=mutation("POST","/deals",sales,Map.of("title","Delete opportunity","amount",10,"stage","LEAD","customer",Map.of("customerId",cid)),1);
        mutation("DELETE","/deals/"+d.get("dealId").asLong(),sales,null,1);
        var companyNode=mutation("POST","/companies",admin,Map.of("name","Delete email company"),1);
        mutation("DELETE","/companies/"+companyNode.get("companyId").asLong(),admin,null,1);
        var account=mutation("POST","/users",admin,Map.of("name","Temporary manager","email","delete-manager@email.test","password","Temp-password-123","role","MANAGER","companyId",company.getCompanyId()),1);
        mail=jobs.count();var deleted=request("DELETE","/users/"+account.get("userId").asLong(),admin,null);assertEquals(204,deleted.status,deleted.raw);assertEquals(mail+1,jobs.count());assertEquals(admin.getEmail(),latest().recipient);
    }
}
