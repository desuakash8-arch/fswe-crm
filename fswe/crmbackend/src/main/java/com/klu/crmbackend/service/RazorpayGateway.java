package com.klu.crmbackend.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

@Service
public class RazorpayGateway {
 @Value("${razorpay.key.id:}") private String keyId;
 @Value("${razorpay.key.secret:}") private String secret;
 @Value("${razorpay.webhook.secret:}") private String webhookSecret;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
 private final JsonMapper json=JsonMapper.builder().build();
 public boolean configured(){return keyId!=null&&keyId.matches("rzp_test_[A-Za-z0-9]+")&&secret!=null&&!secret.isBlank();}
 public String key(){requireConfigured();return keyId;}
 public void requireConfigured(){if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Razorpay test keys are not configured. Ask the CRM administrator to configure them.");}
 public record Order(String id,long amount,String currency,String receipt){}
 public record Payment(String id,String orderId,long amount,String currency,String status,String method,long refunded){}
 /** An uncertain order request must not be automatically repeated with a fresh order. */
 public static class GatewayFailure extends ResponseStatusException {
  public final boolean definite;
  public GatewayFailure(boolean definite){super(HttpStatus.BAD_GATEWAY,"Razorpay could not complete the request. Check configuration or try checking payment status again.");this.definite=definite;}
 }
 private JsonNode call(String method,String path,Object data){
  requireConfigured();
  try {
   var b=HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/"+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Basic "+Base64.getEncoder().encodeToString((keyId+":"+secret).getBytes(StandardCharsets.UTF_8))).header("Content-Type","application/json");
   var r=http.send(b.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofString());
   if(r.statusCode()<200||r.statusCode()>=300)throw new GatewayFailure(r.statusCode()>=400&&r.statusCode()<500);
   return json.readTree(r.body());
  }catch(GatewayFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new GatewayFailure(false);}catch(Exception e){throw new GatewayFailure(false);}
 }
 private Order order(JsonNode n){return new Order(n.path("id").asString(),n.path("amount").asLong(),n.path("currency").asString(),n.path("receipt").asString());}
 private Payment payment(JsonNode n){return new Payment(n.path("id").asString(),n.path("order_id").asString(),n.path("amount").asLong(),n.path("currency").asString(),n.path("status").asString(),n.path("method").asString(),n.path("amount_refunded").asLong());}
 public Order createOrder(long amount,String receipt){return order(call("POST","orders",Map.of("amount",amount,"currency","INR","receipt",receipt,"partial_payment",false)));}
 public Optional<Order> findOrder(String receipt){var matches=new ArrayList<Order>();for(var n:call("GET","orders?count=100&receipt="+URLEncoder.encode(receipt,StandardCharsets.UTF_8),null).path("items")){if(receipt.equals(n.path("receipt").asString()))matches.add(order(n));}if(matches.size()>1)throw new GatewayFailure(false);return matches.stream().findFirst();}
 private String id(String value,String prefix){if(value==null||!value.matches(prefix+"[A-Za-z0-9]+"))throw new IllegalArgumentException("Invalid payment reference");return value;}
 public Payment fetchPayment(String id){return payment(call("GET","payments/"+id(id,"pay_"),null));}
 public List<Payment> payments(String orderId){var list=new ArrayList<Payment>();for(var p:call("GET","orders/"+id(orderId,"order_")+"/payments",null).path("items"))list.add(payment(p));return list;}
 public static boolean validSignature(byte[] body,String signature,String key){
  if(signature==null||!signature.matches("[a-fA-F0-9]{64}")||key==null||key.isBlank())return false;
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature));}catch(Exception e){return false;}
 }
 public boolean verify(String orderId,String paymentId,String signature){requireConfigured();return validSignature((orderId+"|"+paymentId).getBytes(StandardCharsets.UTF_8),signature,secret);}
 public boolean verifyWebhook(byte[] body,String signature){return configured()&&validSignature(body,signature,webhookSecret);}
}
