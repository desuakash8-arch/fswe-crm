package com.klu.crmbackend;

import com.klu.crmbackend.model.Company;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.CompanyRepository;
import com.klu.crmbackend.repository.UserRepository;
import com.klu.crmbackend.service.JwtService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrmApiIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();
    User admin, manager, sales, outsider;
    Company company, otherCompany;
    record Result(int status, JsonNode body, String raw) {}

    Result request(String method, String path, User actor, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (actor != null) builder.header("Authorization", "Bearer " + jwt.generateToken(actor.getUserId(), actor.getEmail(), actor.getRole()));
        var response = http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
            HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()), response.body());
    }
    User user(String name, String role, Company company, User manager) {
        var u = new User();
        u.setName(name); u.setEmail(name + "@test.invalid"); u.setPassword(encoder.encode("test-password"));
        u.setRole(role); u.setCompany(company); u.setManager(manager);
        return users.saveAndFlush(u);
    }
    @BeforeAll void fixtures() {
        company = new Company(); company.setName("Test Company"); company = companies.saveAndFlush(company);
        otherCompany = new Company(); otherCompany.setName("Other Company"); otherCompany = companies.saveAndFlush(otherCompany);
        admin = user("admin", "ADMIN", null, null);
        manager = user("manager", "MANAGER", company, null);
        sales = user("sales", "SALES", company, manager);
        outsider = user("outsider", "SALES", otherCompany, null);
    }
    long customer(User owner, String name) throws Exception {
        Result r = request("POST", "/api/customers", admin,
            "{\"name\":\"" + name + "\",\"status\":\"ACTIVE\",\"assignedTo\":{\"userId\":" + owner.getUserId() + "}}");
        assertEquals(200, r.status, r.raw);
        return r.body.get("customerId").asLong();
    }
    @Test void startupAndSwagger() throws Exception {
        var r = request("GET", "/v3/api-docs", null, null);
        assertEquals(200, r.status, r.raw);
        assertTrue(r.body.has("openapi"));
    }
    @Test void loginValidationAndUnauthorizedStatus() throws Exception {
        assertEquals(401, request("GET", "/api/customers", null, null).status);
        assertEquals(400, request("POST", "/api/auth/login", null, "{}").status);
        assertEquals(401, request("POST", "/api/auth/login", null,
            "{\"email\":\"sales@test.invalid\",\"password\":\"wrong\"}").status);
        var r = request("POST", "/api/auth/login", null,
            "{\"email\":\" SALES@test.invalid \",\"password\":\"test-password\"}");
        assertEquals(200, r.status, r.raw);
        assertTrue(r.body.has("token"));
        assertEquals("SALES", r.body.get("role").asString());
        assertEquals(403, request("POST", "/api/auth/register", null, "{}").status);
        var invalid = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/tasks"))
            .header("Authorization", "Bearer invalid-token").GET().build();
        assertEquals(401, http.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void lookupPermissionsAndNoPasswords() throws Exception {
        assertEquals(403, request("GET", "/api/users", sales, null).status);
        assertEquals(403, request("GET", "/api/companies", sales, null).status);
        var r = request("GET", "/api/users/assignable", sales, null);
        assertEquals(200, r.status, r.raw);
        assertEquals(1, r.body.size());
        assertEquals(sales.getUserId().longValue(), r.body.get(0).get("userId").asLong());
        assertFalse(r.raw.contains("password"));
        r = request("GET", "/api/companies/available", sales, null);
        assertEquals(200, r.status, r.raw);
        assertEquals(company.getCompanyId().longValue(), r.body.get(0).get("companyId").asLong());
    }
    @Test void companyAndSalesRecordIsolation() throws Exception {
        long own = customer(sales, "Own Customer");
        long other = customer(outsider, "Other Customer");
        assertEquals(200, request("GET", "/api/customers/" + own, manager, null).status);
        assertEquals(404, request("GET", "/api/customers/" + other, manager, null).status);
        assertEquals(404, request("GET", "/api/customers/" + own, outsider, null).status);
        var r = request("GET", "/api/customers", manager, null);
        assertEquals(200, r.status, r.raw);
        for (var row : r.body) assertNotEquals(other, row.get("customerId").asLong());
        r = request("PUT", "/api/customers/" + own, manager,
            "{\"name\":\"Forbidden reassignment\",\"assignedTo\":{\"userId\":" + outsider.getUserId() + "}}");
        assertEquals(403, r.status, r.raw);
        assertEquals("Own Customer", request("GET", "/api/customers/" + own, sales, null).body.get("name").asString());
    }
    @Test void interactionNotesAndFollowupTask() throws Exception {
        long c = customer(sales, "Followup Customer");
        var r = request("POST", "/api/interactions", sales,
            "{\"customer\":{\"customerId\":" + c + "},\"type\":\"CALL\",\"notes\":\"Keep these notes\","
            + "\"interactionDate\":\"2026-10-13T10:00:00\",\"nextFollowup\":\"2026-10-15T10:00:00\"}");
        assertEquals(200, r.status, r.raw);
        long id = r.body.get("interactionId").asLong();
        assertEquals("Keep these notes", r.body.get("notes").asString());
        assertFalse(r.raw.contains("password"));
        assertEquals(404, request("GET", "/api/interactions/" + id, outsider, null).status);
        assertEquals(403, request("POST", "/api/interactions/" + id + "/create-task", outsider, null).status);
        r = request("POST", "/api/interactions/" + id + "/create-task", sales, null);
        assertEquals(200, r.status, r.raw);
        assertEquals("2026-10-15", r.body.get("dueDate").asString());
        assertEquals(400, request("POST", "/api/interactions/" + id + "/create-task", sales, null).status);
    }
    @Test void notificationFieldsPersistAndReadToggleWorks() throws Exception {
        var r = request("POST", "/api/notifications", manager,
            "{\"title\":\"Test title\",\"message\":\"Test message\",\"type\":\"GENERAL\",\"user\":{\"userId\":" + sales.getUserId() + "},\"isRead\":false}");
        assertEquals(200, r.status, r.raw);
        long id = r.body.get("notificationId").asLong();
        r = request("GET", "/api/notifications/" + id, sales, null);
        assertEquals("Test title", r.body.get("title").asString());
        assertFalse(r.body.get("isRead").asBoolean());
        assertTrue(r.body.hasNonNull("createdAt"));
        assertEquals(sales.getUserId().longValue(), r.body.get("user").get("userId").asLong());
        assertEquals(403, request("DELETE", "/api/notifications/" + id, outsider, null).status);
        r = request("PUT", "/api/notifications/" + id, sales,
            "{\"title\":\"Test title\",\"message\":\"Test message\",\"type\":\"GENERAL\",\"isRead\":true}");
        assertEquals(200, r.status, r.raw);
        assertTrue(r.body.get("isRead").asBoolean());
    }
    @Test void dealAndTaskUpdatesGenerateNotificationsWithoutSmtp() throws Exception {
        long c = customer(sales, "Deal Customer");
        var r = request("POST", "/api/deals", sales,
            "{\"title\":\"Test Deal\",\"amount\":100,\"stage\":\"LEAD\",\"customer\":{\"customerId\":" + c + "}}");
        assertEquals(200, r.status, r.raw);
        long id = r.body.get("dealId").asLong();
        assertEquals(403, request("PUT", "/api/deals/" + id, outsider, "{\"stage\":\"WON\"}").status);
        r = request("PUT", "/api/deals/" + id, sales,
            "{\"title\":\"Test Deal\",\"amount\":100,\"stage\":\"WON\",\"customer\":{\"customerId\":" + c + "}}");
        assertEquals(200, r.status, r.raw);
        assertEquals("WON", r.body.get("stage").asString());
        r = request("POST", "/api/tasks", sales, "{\"title\":\"Test Task\",\"status\":\"PENDING\",\"priority\":\"HIGH\"}");
        assertEquals(200, r.status, r.raw);
        long taskId = r.body.get("taskId").asLong();
        r = request("PUT", "/api/tasks/" + taskId, sales, "{\"title\":\"Test Task\",\"status\":\"COMPLETED\",\"priority\":\"HIGH\"}");
        assertEquals(200, r.status, r.raw);
        r = request("GET", "/api/notifications/my", sales, null);
        assertEquals(200, r.status, r.raw);
        assertTrue(r.raw.contains("Deal WON"));
        assertTrue(r.raw.contains("Task completed"));
    }
    @Test void managerUserCreationUsesOwnCompany() throws Exception {
        var r = request("POST", "/api/users", manager,
            "{\"name\":\"New Sales\",\"email\":\"new@test.invalid\",\"password\":\"test-password\",\"role\":\"SALES\",\"companyId\":" + otherCompany.getCompanyId() + "}");
        assertEquals(201, r.status, r.raw);
        assertEquals(company.getCompanyId().longValue(), r.body.get("companyId").asLong());
        assertEquals(manager.getUserId().longValue(), r.body.get("managerId").asLong());
        assertFalse(r.raw.contains("password"));
        assertEquals(403, request("POST", "/api/users", manager,
            "{\"name\":\"Bad\",\"email\":\"bad@test.invalid\",\"password\":\"test-password\",\"role\":\"ADMIN\"}").status);
    }
    @Test void corsSupportsLocalhostAndLoopback() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
            var response = http.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        }
    }

    @Test void leadAssignmentAndConversionRespectPermissions() throws Exception {
        var r = request("POST", "/api/leads", admin,
            "{\"name\":\"Conversion Lead\",\"email\":\"conversion@test.invalid\",\"status\":\"NEW\",\"assignedTo\":{\"userId\":" + sales.getUserId() + "}}");
        assertEquals(201, r.status, r.raw);
        long id = r.body.get("leadId").asLong();
        assertEquals(200, request("GET", "/api/leads/" + id, sales, null).status);
        assertEquals(404, request("GET", "/api/leads/" + id, outsider, null).status);
        assertEquals(403, request("DELETE", "/api/leads/" + id, sales, null).status);
        r = request("PUT", "/api/leads/" + id, admin,
            "{\"name\":\"Conversion Lead\",\"email\":\"conversion@test.invalid\",\"status\":\"QUALIFIED\"}");
        assertEquals(200, r.status, r.raw);
        assertEquals(sales.getUserId().longValue(), r.body.get("assignedTo").get("userId").asLong());
        r = request("POST", "/api/leads/" + id + "/convert", sales, null);
        assertEquals(200, r.status, r.raw);
        assertEquals(200, request("GET", "/api/customers/" + r.body.get("customerId").asLong(), sales, null).status);
        assertEquals(400, request("POST", "/api/leads/" + id + "/convert", sales, null).status);
    }

    @Autowired com.klu.crmbackend.repository.TaskRepository tasks;
    @Autowired com.klu.crmbackend.repository.PasswordResetRepository resets;
    @Autowired com.klu.crmbackend.service.ReminderService reminders;
    @Test void paginationSearchAndCustomerTimelineAreScoped() throws Exception {
        long own=customer(sales,"Scoped Search Customer"),other=customer(outsider,"Scoped Search Other");
        var r=request("GET","/api/records/customers?search=Scoped%20Search&size=1",sales,null);
        assertEquals(200,r.status,r.raw);assertEquals(1,r.body.get("totalElements").asInt());assertEquals(own,r.body.get("content").get(0).get("customerId").asLong());
        assertEquals(404,request("GET","/api/customers/"+other+"/detail",sales,null).status);
        assertEquals(400,request("GET","/api/records/deals?size=101",sales,null).status);
    }
    @Test void sameTitleSameDayFollowupsBelongToDifferentInteractions() throws Exception {
        long c=customer(sales,"Shared Followup Name");long first=0;
        for(int n=0;n<2;n++) {
            var r=request("POST","/api/interactions",sales,"{\"customer\":{\"customerId\":"+c+"},\"type\":\"CALL\",\"interactionDate\":\"2026-10-13T10:00:00\",\"nextFollowup\":\"2026-10-14T10:00:00\"}");
            assertEquals(200,r.status,r.raw);long id=r.body.get("interactionId").asLong();if(n==0)first=id;
            var task=request("POST","/api/interactions/"+id+"/create-task",sales,null);assertEquals(200,task.status,task.raw);
            assertEquals(id,task.body.get("interaction").get("interactionId").asLong());assertEquals(c,task.body.get("customer").get("customerId").asLong());
        }
        assertEquals(400,request("POST","/api/interactions/"+first+"/create-task",sales,null).status);
    }
    @Test void conversionCanCreateLinkedOpportunityAndRejectDuplicateConversion() throws Exception {
        var r=request("POST","/api/leads",sales,"{\"name\":\"Linked Lead\",\"email\":\"linked@test.invalid\"}");long id=r.body.get("leadId").asLong();
        r=request("POST","/api/leads/"+id+"/convert",sales,"{\"createDeal\":true,\"amount\":125}");assertEquals(200,r.status,r.raw);
        long customer=r.body.get("customerId").asLong(),deal=r.body.get("dealId").asLong();
        assertEquals(customer,request("GET","/api/leads/"+id,sales,null).body.get("convertedCustomer").get("customerId").asLong());
        assertEquals(customer,request("GET","/api/deals/"+deal,sales,null).body.get("customer").get("customerId").asLong());
        assertEquals(400,request("POST","/api/leads/"+id+"/convert",sales,null).status);
    }
    @Test void invalidRecordsDoNotSaveAndStageHistoryIsVisibleOnlyToOwner() throws Exception {
        assertEquals(400,request("POST","/api/deals",sales,"{\"title\":\"Bad\",\"amount\":-1,\"stage\":\"LEAD\"}").status);
        assertEquals(400,request("POST","/api/customers",sales,"{\"name\":\"Invalid Email\",\"email\":\"bad\"}").status);
        var r=request("POST","/api/deals",sales,"{\"title\":\"History deal\",\"amount\":50,\"stage\":\"LEAD\"}");long id=r.body.get("dealId").asLong();
        assertEquals(400,request("PATCH","/api/deals/"+id+"/stage",sales,"{\"stage\":\"LOST\"}").status);
        r=request("PATCH","/api/deals/"+id+"/stage",sales,"{\"stage\":\"LOST\",\"lostReason\":\"Budget\"}");assertEquals(200,r.status,r.raw);assertTrue(r.body.hasNonNull("closedDate"));
        assertTrue(request("GET","/api/deals/"+id+"/history",sales,null).raw.contains("Budget"));assertEquals(404,request("GET","/api/deals/"+id+"/history",outsider,null).status);
        assertEquals(403,request("GET","/api/audit",sales,null).status);
    }
    @Test void reportsExcludeClosedDealsFromOpenPipeline() throws Exception {
        var u=user("report-owner","SALES",company,manager);
        for(String stage:new String[]{"LEAD","WON","LOST"})assertEquals(200,request("POST","/api/deals",u,"{\"title\":\"Report deal\",\"amount\":100,\"stage\":\""+stage+"\",\"lostReason\":\"Budget\"}").status);
        var r=request("GET","/api/reports?from="+java.time.LocalDate.now().withDayOfMonth(1)+"&to="+java.time.LocalDate.now(),u,null);
        assertEquals(200,r.status,r.raw);assertEquals(100,r.body.get("openPipeline").asInt());assertEquals(100,r.body.get("wonValue").asInt());assertEquals(50,r.body.get("winRate").asInt());
    }
    @Test void remindersAreDeduplicatedAndCompletedTasksAreSkipped() {
        var t=new com.klu.crmbackend.model.Task();t.setTitle("Reminder unit");t.setAssignedTo(sales);t.setStatus("PENDING");t.setDueDate(java.time.LocalDate.now());t=tasks.saveAndFlush(t);
        reminders.run();String key=tasks.findById(t.getTaskId()).orElseThrow().getReminderKey();assertTrue(key.endsWith("DUE_TODAY"));reminders.run();assertEquals(key,tasks.findById(t.getTaskId()).orElseThrow().getReminderKey());
    }
    @Test void deactivationAndPasswordResetInvalidateSessions() throws Exception {
        var target=user("account-test","SALES",company,manager);
        String token=jwt.generateToken(target.getUserId(),target.getEmail(),target.getRole());
        assertEquals(200,request("PATCH","/api/users/"+target.getUserId()+"/active",manager,"{\"active\":false}").status);
        assertEquals(401,request("GET","/api/customers",target,null).status);
        assertEquals(401,request("POST","/api/auth/login",null,"{\"email\":\"account-test@test.invalid\",\"password\":\"test-password\"}").status);
        assertEquals(200,request("PATCH","/api/users/"+target.getUserId()+"/active",manager,"{\"active\":true}").status);
        var reset=new com.klu.crmbackend.model.PasswordReset();reset.tokenHash=com.klu.crmbackend.service.AccountService.hash("test-reset-token");reset.userId=target.getUserId();reset.expiresAt=java.time.LocalDateTime.now().plusMinutes(5);resets.saveAndFlush(reset);
        assertEquals(200,request("POST","/api/auth/reset-password",null,"{\"token\":\"test-reset-token\",\"password\":\"new-password-value\"}").status);
        assertEquals(400,request("POST","/api/auth/reset-password",null,"{\"token\":\"test-reset-token\",\"password\":\"new-password-value\"}").status);
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/tasks")).header("Authorization","Bearer "+token).GET().build();assertEquals(401,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Autowired com.klu.crmbackend.service.EmailQueue emailQueue;
    @Autowired com.klu.crmbackend.service.EmailService emailSender;
    @Autowired com.klu.crmbackend.repository.EmailJobRepository emailJobs;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void outboxRollsBackWithBusinessTransaction() {
        long before=emailJobs.count();
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender,"enabled",true);
        try {
            var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
            assertThrows(IllegalStateException.class,()->transaction.execute(status->{emailQueue.enqueue("test@test.invalid","Test","No real email",sales.getUserId(),null);throw new IllegalStateException("Business save failed");}));
            assertEquals(before,emailJobs.count());
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(emailSender,"enabled",false);}
    }
    @Test void csvPreviewDoesNotWriteAndDuplicateImportDoesNotRepeat() throws Exception {
        String csv="name,email\nCSV API,csv-api@test.invalid\nDuplicate,csv-api@test.invalid\n";
        for(boolean commit:new boolean[]{false,true,true}) {
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/data/customers/import?commit="+commit)).header("Content-Type","text/plain")
                .header("Authorization","Bearer "+jwt.generateToken(sales.getUserId(),sales.getEmail(),sales.getRole())).POST(HttpRequest.BodyPublishers.ofString(csv)).build();
            var response=http.send(req,HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode(),response.body());
            if(!commit)assertEquals(0,json.readTree(response.body()).get("imported").asInt());
        }
        var r=request("GET","/api/records/customers?search=csv-api",sales,null);assertEquals(1,r.body.get("totalElements").asInt());
    }
    @Test void failedOpportunityCreationRollsBackConversion() throws Exception {
        var r=request("POST","/api/leads",sales,"{\"name\":\"Rollback Lead\",\"email\":\"rollback@test.invalid\"}");long id=r.body.get("leadId").asLong();
        assertEquals(400,request("POST","/api/leads/"+id+"/convert",sales,"{\"createDeal\":true,\"amount\":-1}").status);
        assertEquals("NEW",request("GET","/api/leads/"+id,sales,null).body.get("status").asString());
        assertEquals(0,request("GET","/api/records/customers?search=rollback@test.invalid",sales,null).body.get("totalElements").asInt());
    }

    long healthDeal(long customerId,String title) throws Exception {
        var r=request("POST","/api/deals",sales,"{\"title\":\""+title+"\",\"amount\":100,\"stage\":\"PROPOSAL\",\"expectedCloseDate\":\""+java.time.LocalDate.now().plusDays(10)+"\",\"customer\":{\"customerId\":"+customerId+"}}");
        assertEquals(200,r.status,r.raw);return r.body.get("dealId").asLong();
    }
    @Test void dealHealthUsesOnlyLinkedActivityAndEnforcesAccess() throws Exception {
        long c=customer(sales,"Health Customer"),a=healthDeal(c,"Health A"),b=healthDeal(c,"Health B");
        String date=java.time.LocalDateTime.now().minusHours(2).toString(),next=java.time.LocalDateTime.now().plusDays(1).toString();
        var interaction=request("POST","/api/interactions",sales,"{\"type\":\"CALL\",\"customer\":{\"customerId\":"+c+"},\"deal\":{\"dealId\":"+a+"},\"interactionDate\":\""+date+"\",\"nextFollowup\":\""+next+"\"}");
        assertEquals(200,interaction.status,interaction.raw);assertEquals(a,interaction.body.get("linkedDealId").asLong());assertFalse(interaction.body.has("deal"));
        assertEquals("ON_TRACK",request("GET","/api/deals/"+a+"/health",sales,null).body.get("status").asString());
        assertEquals("NEEDS_ATTENTION",request("GET","/api/deals/"+b+"/health",sales,null).body.get("status").asString());
        assertEquals(404,request("GET","/api/deals/"+a+"/health",outsider,null).status);
        assertEquals(0,request("GET","/api/deals/health?ids="+a+","+b,outsider,null).body.size());
        assertEquals(2,request("GET","/api/deals/health?ids="+a+","+b,manager,null).body.size());
        assertEquals(2,request("GET","/api/deals/health?ids="+a+","+b,admin,null).body.size());
        assertEquals(404,request("GET","/api/records/interactions?dealId="+a,outsider,null).status);
        assertEquals(1,request("GET","/api/records/interactions?dealId="+a,sales,null).body.get("totalElements").asInt());
        assertEquals(0,request("GET","/api/records/interactions?dealId="+b,sales,null).body.get("totalElements").asInt());
        var task=request("POST","/api/interactions/"+interaction.body.get("interactionId").asLong()+"/create-task",sales,null);
        assertEquals(200,task.status,task.raw);assertEquals(a,task.body.get("linkedDealId").asLong());
        assertEquals(1,request("GET","/api/records/tasks?dealId="+a,sales,null).body.get("totalElements").asInt());
        assertEquals(400,request("GET","/api/records/customers?dealId="+a,sales,null).status);
        assertEquals(400,request("GET","/api/deals/health?ids=0",sales,null).status);
    }
    @Test void dealLinkValidationAndClosingDateUpdatesAreScoped() throws Exception {
        long c=customer(sales,"Health Link Customer"),other=customer(sales,"Health Different Customer"),d=healthDeal(c,"Health Validation");
        assertEquals(400,request("POST","/api/tasks",sales,"{\"title\":\"Mismatch\",\"customer\":{\"customerId\":"+other+"},\"deal\":{\"dealId\":"+d+"}}").status);
        assertEquals(404,request("POST","/api/tasks",outsider,"{\"title\":\"Hidden deal\",\"deal\":{\"dealId\":"+d+"}}").status);
        var r=request("PATCH","/api/deals/"+d+"/expected-close",sales,"{\"expectedCloseDate\":\""+java.time.LocalDate.now().minusDays(1)+"\"}");
        assertEquals(200,r.status,r.raw);assertEquals("PROPOSAL",r.body.get("stage").asString());
        assertEquals("AT_RISK",request("GET","/api/deals/"+d+"/health",sales,null).body.get("status").asString());
        assertEquals(404,request("PATCH","/api/deals/"+d+"/expected-close",outsider,"{\"expectedCloseDate\":null}").status);
        assertEquals(200,request("PATCH","/api/deals/"+d+"/stage",sales,"{\"stage\":\"WON\"}").status);
        assertEquals("CLOSED",request("GET","/api/deals/"+d+"/health",sales,null).body.get("status").asString());
    }
    @Test void attachingHistoricalInteractionAlsoLinksItsFollowup() throws Exception {
        long c=customer(sales,"Health Historical"),d=healthDeal(c,"Health Historical Deal");
        String body="{\"type\":\"CALL\",\"customer\":{\"customerId\":"+c+"},\"interactionDate\":\"2026-01-01T10:00:00\",\"nextFollowup\":\"2026-01-02T10:00:00\"}";
        var i=request("POST","/api/interactions",sales,body);assertEquals(200,i.status,i.raw);long id=i.body.get("interactionId").asLong();
        var t=request("POST","/api/interactions/"+id+"/create-task",sales,null);assertEquals(200,t.status,t.raw);
        assertEquals(200,request("PUT","/api/interactions/"+id,sales,body.substring(0,body.length()-1)+",\"deal\":{\"dealId\":"+d+"}}").status);
        assertEquals(d,request("GET","/api/tasks/"+t.body.get("taskId").asLong(),sales,null).body.get("linkedDealId").asLong());
    }
}
