package com.klu.crmbackend;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;import java.net.http.*;import java.math.BigDecimal;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.datasource.url=jdbc:h2:mem:payment_tests;MODE=MySQL;DB_CLOSE_DELAY=-1")
class PaymentIntegrationTests {
 @Value("${local.server.port}")int port;
 @Autowired UserRepository users;@Autowired CustomerRepository customers;@Autowired DealRepository deals;@Autowired PaymentInvoiceRepository invoices;@Autowired PaymentAttemptRepository attempts;@Autowired NotificationRepository notifications;@Autowired JwtService jwt;
 @MockitoBean RazorpayGateway gateway;
 @MockitoBean EmailService sender;
 @Autowired EmailJobRepository emailJobs;
 final JsonMapper json=JsonMapper.builder().build();final HttpClient http=HttpClient.newHttpClient();User owner,outsider;Deal deal;
 record R(int status,JsonNode body,String raw){}
 R req(String method,String path,User actor,Object data)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api"+path)).header("Content-Type","application/json");if(actor!=null)b.header("Authorization","Bearer "+jwt.generateToken(actor.getUserId(),actor.getEmail(),actor.getRole()));var r=http.send(b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());return new R(r.statusCode(),r.body().isBlank()?null:json.readTree(r.body()),r.body());}
 User user(){var u=new User();u.setName("Payment tester");u.setEmail(UUID.randomUUID()+"@test.invalid");u.setPassword("hash");u.setRole("SALES");return users.saveAndFlush(u);}
 @BeforeEach void setup(){owner=user();outsider=user();var c=new Customer();c.setName("Payment customer");c.setEmail("customer@test.invalid");c.setAssignedTo(owner);customers.saveAndFlush(c);deal=new Deal();deal.setTitle("Payment opportunity");deal.setStage("WON");deal.setAmount(new BigDecimal("250.25"));deal.setAssignedTo(owner);deal.setCustomer(c);deals.saveAndFlush(deal);when(gateway.configured()).thenReturn(true);when(gateway.key()).thenReturn("rzp_test_example");when(gateway.findOrder(anyString())).thenReturn(Optional.empty());when(gateway.createOrder(anyLong(),anyString())).thenAnswer(a->new RazorpayGateway.Order("order_"+deal.getDealId(),a.getArgument(0),"INR",a.getArgument(1)));when(gateway.verify(anyString(),anyString(),anyString())).thenReturn(true);when(gateway.payments(anyString())).thenReturn(List.of());}
 JsonNode create()throws Exception{var r=req("POST","/invoices",owner,Map.of("dealId",deal.getDealId()));assertEquals(200,r.status,r.raw);return r.body;}
 String order(String token)throws Exception{var r=req("POST","/public/payments/order",null,Map.of("token",token));assertEquals(200,r.status,r.raw);assertEquals(25025,r.body.get("amountMinor").asLong());return r.body.get("orderId").asString();}
 R verifyPayment(String token,String order,String payment)throws Exception{return req("POST","/public/payments/verify",null,Map.of("token",token,"razorpay_order_id",order,"razorpay_payment_id",payment,"razorpay_signature","test-signature"));}
 @Test void invoiceScopeTokenRotationExpiryAndFixedAmount()throws Exception{var c=create();long id=c.path("invoice").path("id").asLong();String token=c.path("token").asString();assertFalse(invoices.findById(id).orElseThrow().tokenHash.equals(token));assertEquals(404,req("GET","/invoices/"+id,outsider,null).status);assertEquals(401,req("GET","/invoices",null,null).status);assertEquals(404,req("POST","/public/payments/details",null,Map.of("token","invalid")).status);assertEquals(409,req("POST","/invoices",owner,Map.of("dealId",deal.getDealId())).status);assertEquals(400,req("PUT","/deals/"+deal.getDealId(),owner,Map.of("title","Changed","amount",999,"stage","WON")).status);
  var rotated=req("POST","/invoices/"+id+"/link",owner,null);assertEquals(404,req("POST","/public/payments/details",null,Map.of("token",token)).status);token=rotated.body.path("token").asString();var invoice=invoices.findById(id).orElseThrow();invoice.linkExpiresAt=LocalDateTime.now().minusMinutes(1);invoices.saveAndFlush(invoice);assertEquals(410,req("POST","/public/payments/details",null,Map.of("token",token)).status);
 }
 @Test void captureVerifiedOnServerIsIdempotentAndHistoryDoesNotRegress()throws Exception{var c=create();String token=c.get("token").asString(),order=order(token),payment="pay_"+deal.getDealId();order(token);verify(gateway,times(1)).createOrder(anyLong(),anyString());long n=notifications.count();when(gateway.fetchPayment(payment)).thenReturn(new RazorpayGateway.Payment(payment,order,25025,"INR","captured","card",0));var r=verifyPayment(token,order,payment);assertEquals(200,r.status,r.raw);assertEquals("PAID",r.body.path("status").asString());assertEquals(n+1,notifications.count());verifyPayment(token,order,payment);assertEquals(n+1,notifications.count());when(gateway.fetchPayment(payment)).thenReturn(new RazorpayGateway.Payment(payment,order,25025,"INR","failed","card",0));assertEquals("PAID",verifyPayment(token,order,payment).body.path("status").asString());assertEquals(409,req("POST","/public/payments/order",null,Map.of("token",token)).status);}
 @Test void invalidSignaturesAndAmountsCannotMarkPaid()throws Exception{var c=create();String token=c.get("token").asString(),order=order(token);when(gateway.verify(anyString(),anyString(),anyString())).thenReturn(false);assertEquals(400,verifyPayment(token,order,"pay_bad").status);verify(gateway,never()).fetchPayment(anyString());when(gateway.verify(anyString(),anyString(),anyString())).thenReturn(true);when(gateway.fetchPayment("pay_bad")).thenReturn(new RazorpayGateway.Payment("pay_bad",order,1,"INR","captured","upi",0));assertEquals(400,verifyPayment(token,order,"pay_bad").status);assertEquals("UNPAID",req("POST","/public/payments/details",null,Map.of("token",token)).body.path("status").asString());}
 @Test void authorizedIsNotPaidAndCanRecoverWithoutBrowserCallback()throws Exception{var c=create();String token=c.get("token").asString(),order=order(token),payment="pay_"+deal.getDealId();when(gateway.fetchPayment(payment)).thenReturn(new RazorpayGateway.Payment(payment,order,25025,"INR","authorized","upi",0));assertEquals("PROCESSING",verifyPayment(token,order,payment).body.path("status").asString());when(gateway.payments(order)).thenReturn(List.of(new RazorpayGateway.Payment(payment,order,25025,"INR","captured","upi",0)));assertEquals("PAID",req("POST","/public/payments/status",null,Map.of("token",token)).body.path("status").asString());}
 @Test void uncertainOrderIsRecoveredByReceiptWithoutCreatingAnotherOrder()throws Exception{var c=create();String token=c.get("token").asString();when(gateway.createOrder(anyLong(),anyString())).thenThrow(new RazorpayGateway.GatewayFailure(false));assertEquals(502,req("POST","/public/payments/order",null,Map.of("token",token)).status);assertEquals(409,req("POST","/public/payments/order",null,Map.of("token",token)).status);verify(gateway,times(1)).createOrder(anyLong(),anyString());var invoice=invoices.findById(c.path("invoice").path("id").asLong()).orElseThrow();when(gateway.findOrder(invoice.receipt)).thenReturn(Optional.of(new RazorpayGateway.Order("order_recovered",25025,"INR",invoice.receipt)));assertEquals("order_recovered",order(token));}
 @Test void webhookNeedsSignatureAndDuplicateEventsDoNotDuplicateReceipts()throws Exception{var c=create();String token=c.get("token").asString(),order=order(token),payment="pay_"+deal.getDealId();var payload=Map.of("event","payment.captured","payload",Map.of("payment",Map.of("entity",Map.of("order_id",order,"id",payment))));assertEquals(400,req("POST","/public/payments/webhook",null,payload).status);when(gateway.verifyWebhook(any(),nullable(String.class))).thenReturn(true);when(gateway.fetchPayment(payment)).thenReturn(new RazorpayGateway.Payment(payment,order,25025,"INR","captured","card",0));long n=notifications.count();assertEquals(200,req("POST","/public/payments/webhook",null,payload).status);assertEquals(200,req("POST","/public/payments/webhook",null,payload).status);assertEquals(n+1,notifications.count());}
 void enableDemoEmail(){when(sender.isEnabled()).thenReturn(true);when(sender.testRouting()).thenReturn(true);when(sender.recipient(anyString())).thenReturn("demo@tests.invalid");}
 EmailJob latestPaymentLink(){return emailJobs.findAll().stream().filter(j->Objects.equals(j.dealId,deal.getDealId())&&j.subject.contains("Payment link for")).max(Comparator.comparing(j->j.id)).orElseThrow();}
 @Test void newInvoiceQueuesClickablePaymentLinkUsingDemoRouting()throws Exception{
  enableDemoEmail();var result=create();String token=result.get("token").asString();var job=latestPaymentLink();
  assertTrue(result.path("delivery").path("queued").asBoolean());assertEquals("demo@tests.invalid",job.recipient);assertEquals("PENDING",job.state);
  assertTrue(job.body.contains("Original recipient: customer@test.invalid"));assertTrue(job.body.contains("http://127.0.0.1:5173/pay#"+token));assertTrue(job.body.contains("INR 250.25"));assertTrue(job.body.contains("TEST MODE"));assertTrue(job.body.contains("No CRM login is required"));
  assertEquals(200,req("POST","/public/payments/details",null,Map.of("token",token)).status);
  verify(sender,never()).sendDirectEmail(anyString(),anyString(),anyString());
 }
 @Test void manualEmailReusesActiveLinkAndEnforcesAccessAndTokenOwnership()throws Exception{
  var result=create();long id=result.path("invoice").path("id").asLong();String token=result.get("token").asString();String hash=invoices.findById(id).orElseThrow().tokenHash;enableDemoEmail();
  assertEquals(401,req("POST","/invoices/"+id+"/email",null,Map.of("token",token)).status);
  assertEquals(404,req("POST","/invoices/"+id+"/email",outsider,Map.of("token",token)).status);
  long count=emailJobs.count();assertEquals(409,req("POST","/invoices/"+id+"/email",owner,Map.of("token","a".repeat(43))).status);assertEquals(count,emailJobs.count());
  var sent=req("POST","/invoices/"+id+"/email",owner,Map.of("token",token));assertEquals(200,sent.status,sent.raw);assertTrue(sent.body.path("delivery").path("queued").asBoolean());assertEquals(token,sent.body.get("token").asString());assertEquals(hash,invoices.findById(id).orElseThrow().tokenHash);
  assertTrue(latestPaymentLink().body.contains("/pay#"+token));
  var fresh=req("POST","/invoices/"+id+"/email",owner,Map.of());assertEquals(200,fresh.status,fresh.raw);String replacement=fresh.body.get("token").asString();assertNotEquals(token,replacement);
  assertEquals(404,req("POST","/public/payments/details",null,Map.of("token",token)).status);assertEquals(200,req("POST","/public/payments/details",null,Map.of("token",replacement)).status);
 }
 @Test void disabledEmailMissingCustomerAddressAndUnreadyCheckoutPreserveExistingLinks()throws Exception{
  var customer=customers.findById(deal.getCustomer().getCustomerId()).orElseThrow();customer.setEmail(null);customers.saveAndFlush(customer);enableDemoEmail();
  var result=create();assertFalse(result.path("delivery").path("queued").asBoolean());long id=result.path("invoice").path("id").asLong();String hash=invoices.findById(id).orElseThrow().tokenHash;
  assertEquals(409,req("POST","/invoices/"+id+"/email",owner,Map.of()).status);assertEquals(hash,invoices.findById(id).orElseThrow().tokenHash);
  customer.setEmail("corrected@tests.invalid");customers.saveAndFlush(customer);
  when(sender.isEnabled()).thenReturn(false);assertEquals(409,req("POST","/invoices/"+id+"/email",owner,Map.of()).status);assertEquals(hash,invoices.findById(id).orElseThrow().tokenHash);
  enableDemoEmail();doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Configure checkout")).when(gateway).requireConfigured();
  assertEquals(503,req("POST","/invoices/"+id+"/email",owner,Map.of()).status);assertEquals(hash,invoices.findById(id).orElseThrow().tokenHash);
  doNothing().when(gateway).requireConfigured();var sent=req("POST","/invoices/"+id+"/email",owner,Map.of());assertEquals(200,sent.status,sent.raw);assertEquals("corrected@tests.invalid",sent.body.path("invoice").path("customerEmail").asString());
 }
 @Test void paymentRequestsCannotBeEmailedForProcessingOrSettledInvoices()throws Exception{
  var result=create();long id=result.path("invoice").path("id").asLong();enableDemoEmail();var invoice=invoices.findById(id).orElseThrow();String hash=invoice.tokenHash;
  invoice.status="PROCESSING";invoices.saveAndFlush(invoice);assertEquals(409,req("POST","/invoices/"+id+"/email",owner,Map.of()).status);
  invoice.status="PAID";invoice.paidAt=LocalDateTime.now();invoices.saveAndFlush(invoice);assertEquals(409,req("POST","/invoices/"+id+"/email",owner,Map.of()).status);assertEquals(hash,invoices.findById(id).orElseThrow().tokenHash);
 }
 @Test void hmacRejectsTamperingAndLiveKeysAreDisabled()throws Exception{byte[] raw="order_test|pay_test".getBytes(java.nio.charset.StandardCharsets.UTF_8);var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("secret".getBytes(),"HmacSHA256"));String signature=HexFormat.of().formatHex(mac.doFinal(raw));assertTrue(RazorpayGateway.validSignature(raw,signature,"secret"));assertFalse(RazorpayGateway.validSignature("tampered".getBytes(),signature,"secret"));assertFalse(RazorpayGateway.validSignature(raw,"bad","secret"));var real=new RazorpayGateway();org.springframework.test.util.ReflectionTestUtils.setField(real,"keyId","rzp_live_example");org.springframework.test.util.ReflectionTestUtils.setField(real,"secret","secret");assertFalse(real.configured());}
}
