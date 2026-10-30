package com.klu.crmbackend;

import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:assistant_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssistantIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users; @Autowired CompanyRepository companies;
    @Autowired CustomerRepository customers; @Autowired DealRepository deals;
    @Autowired TaskRepository tasks; @Autowired InteractionRepository interactions;
    @Autowired EmailJobRepository emails; @Autowired JwtService jwt;
    @Autowired AssistantService service; @Autowired PlatformTransactionManager transactions;
    @MockitoBean GroqAssistantClient groq;
    final JsonMapper json=JsonMapper.builder().build(); final HttpClient http=HttpClient.newHttpClient();
    User admin,manager,sales,colleague,other,peerManager;
    Customer customer,hidden,otherCustomer; Deal deal,hiddenDeal,unrelatedDeal;
    AssistantContextService.Context captured;

    @BeforeAll void seed() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var a=new Company();a.setName("Company A");a=companies.save(a);
            var b=new Company();b.setName("Company B");b=companies.save(b);
            admin=user("admin","ADMIN",a); manager=user("manager","MANAGER",a);
            sales=user("sales","SALES",a); colleague=user("colleague","SALES",a);
            other=user("other","SALES",b);peerManager=user("peer-manager","MANAGER",a);
            customer=customer("Visible customer",sales);hidden=customer("Hidden company B",other);
            otherCustomer=customer("Second customer",sales);
            deal=deal("Visible proposal",customer,sales);hiddenDeal=deal("Hidden opportunity",hidden,other);
            unrelatedDeal=deal("Unrelated opportunity",otherCustomer,sales);
            deal("Colleague opportunity",customer,colleague);deal("Peer manager opportunity",customer,peerManager);
            for(int n=0;n<12;n++)task("Due task "+n,sales,LocalDate.now().minusDays(n),"PENDING",null);
            task("Other team task",other,LocalDate.now().minusDays(1),"PENDING",null);
            task("Completed work",sales,LocalDate.now().minusDays(1),"COMPLETED",null);
            task("Future work",sales,LocalDate.now().plusDays(3),"PENDING",null);
            var completed=call("Completed follow-up",customer,sales,deal,LocalDateTime.now().minusDays(5));
            task("Handled follow-up",sales,LocalDate.now().minusDays(4),"COMPLETED",completed);
            call("Old superseded note",customer,sales,unrelatedDeal,LocalDateTime.now().minusDays(6));
            var recent=call("Current customer request",customer,sales,unrelatedDeal,LocalDateTime.now().minusHours(2));
            recent.setNextFollowup(LocalDateTime.now().plusDays(3));interactions.save(recent);
            call("Due unconverted follow-up",otherCustomer,sales,null,LocalDateTime.now().minusDays(2));
            call("Future fake contact",customer,sales,deal,LocalDateTime.now().plusDays(1));
            call("Hidden interaction",customer,other,deal,LocalDateTime.now().minusHours(1));
        });
    }
    @BeforeEach void mockProvider() {
        ((Map<?,?>)Objects.requireNonNull(ReflectionTestUtils.getField(service,"usage"))).clear();
        captured=null;
        when(groq.configured()).thenReturn(true);when(groq.model()).thenReturn("openai/gpt-oss-20b");
        when(groq.generate(any(),anyString(),any())).thenAnswer(invocation -> {
            captured=invocation.getArgument(2);
            return new GroqAssistantClient.Answer("Review your recorded CRM activity.",List.of(),"Follow up","Hello,\nMay we discuss the next step?");
        });
    }
    User user(String name,String role,Company company){var u=new User();u.setName(name);u.setRole(role);u.setEmail(name+"@assistant.test");u.setPassword("private-password-hash");u.setCompany(company);return users.save(u);}
    Customer customer(String name,User owner){var c=new Customer();c.setName(name);c.setEmail(name.replace(" ","")+"@private.invalid");c.setPhone("9876543210");c.setStatus("ACTIVE");c.setAssignedTo(owner);return customers.save(c);}
    Deal deal(String title,Customer c,User owner){var d=new Deal();d.setTitle(title);d.setCustomer(c);d.setAssignedTo(owner);d.setStage("PROPOSAL");d.setAmount(new BigDecimal("2500.00"));d.setExpectedCloseDate(LocalDate.now().minusDays(1));return deals.save(d);}
    Interaction call(String notes,Customer c,User owner,Deal d,LocalDateTime date){var i=new Interaction();i.setNotes(notes);i.setType("CALL");i.setCustomer(c);i.setUser(owner);i.setDeal(d);i.setInteractionDate(date);i.setNextFollowup(date.plusHours(1));return interactions.save(i);}
    void task(String title,User owner,LocalDate date,String state,Interaction linked){var t=new Task();t.setTitle(title);t.setAssignedTo(owner);t.setDueDate(date);t.setStatus(state);t.setPriority("HIGH");t.setCustomer(customer);t.setInteraction(linked);tasks.save(t);}
    record Result(int status,JsonNode body,String raw,String cache){}
    Result request(User user,String method,String path,Object data)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/assistant"+path)).header("Content-Type","application/json");
        if(user!=null)request.header("Authorization","Bearer "+jwt.generateToken(user.getUserId(),user.getEmail(),user.getRole()));
        var r=http.send(request.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
        return new Result(r.statusCode(),json.readTree(r.body()),r.body(),r.headers().firstValue("cache-control").orElse(""));
    }
    Result ask(User user,String mode,Long cid,Long did)throws Exception {
        var body=new HashMap<String,Object>();body.put("mode",mode);body.put("customerId",cid);body.put("dealId",did);body.put("question","Summarize the selected records");
        return request(user,"POST","/ask",body);
    }

    @Test void authenticationAndValidationRunBeforeProviderCalls()throws Exception {
        assertEquals(401,request(null,"GET","/config",null).status);
        assertEquals(401,ask(null,"DAILY_PRIORITIES",null,null).status);
        assertEquals(400,ask(sales,"INVALID_MODE",null,null).status);
        assertEquals(400,ask(sales,"DEAL_HEALTH",null,null).status);
        assertEquals(400,request(sales,"POST","/ask",Map.of("mode","DAILY_PRIORITIES","question","X".repeat(1501))).status);
        verify(groq,never()).generate(any(),anyString(),any());
    }
    @Test void hiddenIdsAndMismatchedCustomersCannotReachGroq()throws Exception {
        assertEquals(404,ask(sales,"CUSTOMER_SUMMARY",hidden.getCustomerId(),null).status);
        assertEquals(404,ask(sales,"DEAL_HEALTH",null,hiddenDeal.getDealId()).status);
        assertEquals(404,ask(manager,"EMAIL_DRAFT",hidden.getCustomerId(),null).status);
        assertEquals(400,ask(sales,"EMAIL_DRAFT",customer.getCustomerId(),unrelatedDeal.getDealId()).status);
        verify(groq,never()).generate(any(),anyString(),any());
    }
    @Test void customerSnapshotIncludesOnlyAuthorizedActivityAndNoContactOrAccountSecrets()throws Exception {
        var result=ask(sales,"CUSTOMER_SUMMARY",customer.getCustomerId(),null);assertEquals(200,result.status,result.raw);
        String snapshot=json.writeValueAsString(captured.data());
        assertTrue(snapshot.contains("Visible proposal"));assertTrue(snapshot.contains("Current customer request"));
        for(String hiddenValue:List.of("Hidden interaction","Hidden opportunity","Colleague opportunity","Peer manager opportunity","Future fake contact","@private.invalid","@assistant.test","9876543210","private-password-hash"))assertFalse(snapshot.contains(hiddenValue),hiddenValue);
        assertTrue(result.body.path("draft").isNull());assertTrue(result.cache.contains("no-store"));
    }
    @Test void managerAndAdminSnapshotsUseTheirActualScopes()throws Exception {
        assertEquals(200,ask(manager,"CUSTOMER_SUMMARY",customer.getCustomerId(),null).status);
        String managerSnapshot=json.writeValueAsString(captured.data());
        assertTrue(managerSnapshot.contains("Colleague opportunity"));assertFalse(managerSnapshot.contains("Peer manager opportunity"));assertFalse(managerSnapshot.contains("Hidden interaction"));
        assertEquals(200,ask(admin,"CUSTOMER_SUMMARY",customer.getCustomerId(),null).status);
        String adminSnapshot=json.writeValueAsString(captured.data());assertTrue(adminSnapshot.contains("Peer manager opportunity"));assertTrue(adminSnapshot.contains("Hidden interaction"));
    }
    @Test void prioritiesAreBoundedAndSuppressHandledAndSupersededFollowups()throws Exception {
        var result=ask(sales,"DAILY_PRIORITIES",null,null);assertEquals(200,result.status,result.raw);
        assertEquals(12L,captured.data().get("dueTaskCount"));assertEquals(10,((List<?>)captured.data().get("dueTasks")).size());
        assertEquals(1L,captured.data().get("dueFollowupCount"));
        String followups=json.writeValueAsString(captured.data().get("dueFollowups"));
        assertTrue(followups.contains("Due unconverted follow-up"));assertFalse(followups.contains("Old superseded note"));assertFalse(followups.contains("Completed follow-up"));
        assertFalse(json.writeValueAsString(captured.data()).contains("Other team task"));
    }
    @Test void healthUsesExistingReasonsAndDraftGenerationDoesNotWriteOrEmail()throws Exception {
        var health=ask(sales,"DEAL_HEALTH",null,deal.getDealId());assertEquals(200,health.status,health.raw);
        assertTrue(health.raw.contains("Recorded health: AT RISK"));
        assertEquals("AT_RISK",((DealHealthService.Health)captured.data().get("health")).status());
        long taskCount=tasks.count(),emailCount=emails.count(),interactionCount=interactions.count();
        var draft=ask(sales,"EMAIL_DRAFT",customer.getCustomerId(),deal.getDealId());assertEquals(200,draft.status,draft.raw);
        assertFalse(draft.body.path("draft").path("body").asString().isBlank());
        assertFalse(json.writeValueAsString(captured.data()).contains("Current customer request"),"Draft must not include other deal activity");
        assertEquals(taskCount,tasks.count());assertEquals(emailCount,emails.count());assertEquals(interactionCount,interactions.count());
    }
}
