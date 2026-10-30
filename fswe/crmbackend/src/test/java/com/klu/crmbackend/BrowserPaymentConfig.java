package com.klu.crmbackend;
import com.klu.crmbackend.service.RazorpayGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Test-only gateway: never packaged in the application or used for real Razorpay checkout. */
@TestConfiguration
public class BrowserPaymentConfig {
 @Bean @Primary com.klu.crmbackend.service.EmailService browserEmail(){
  var beans=new org.springframework.beans.factory.support.StaticListableBeanFactory();
  return new com.klu.crmbackend.service.EmailService(beans.getBeanProvider(org.springframework.mail.javamail.JavaMailSender.class), new com.klu.crmbackend.service.ResendEmailSender("")){
   @Override public boolean isEnabled(){return true;}
   @Override public boolean testRouting(){return true;}
   @Override public String recipient(String original){return "demo@browser.test";}
   @Override public com.klu.crmbackend.dto.EmailDeliveryResult sendDirectEmail(String to,String subject,String body){throw new IllegalStateException("Browser tests must not send SMTP email");}
  };
 }
 @Bean @Primary RazorpayGateway browserGateway(){return new RazorpayGateway(){
  final Map<String,Order> orders=new ConcurrentHashMap<>();
  @Override public boolean configured(){return true;}
  @Override public void requireConfigured(){}
  @Override public String key(){return "rzp_test_browser";}
  @Override public Optional<Order> findOrder(String receipt){return orders.values().stream().filter(o->o.receipt().equals(receipt)).findFirst();}
  @Override public Order createOrder(long amount,String receipt){var o=new Order("order_"+UUID.randomUUID().toString().replace("-",""),amount,"INR",receipt);orders.put(o.id(),o);return o;}
  @Override public boolean verify(String order,String payment,String signature){return validSignature((order+"|"+payment).getBytes(java.nio.charset.StandardCharsets.UTF_8),signature,"browser-payment-secret");}
  @Override public Payment fetchPayment(String id){var order=orders.get("order_"+id.substring(4));if(order==null)throw new IllegalArgumentException("Unknown test payment");return new Payment(id,order.id(),order.amount(),"INR","captured","card",0);}
  @Override public List<Payment> payments(String order){return List.of();}
 };}
}
