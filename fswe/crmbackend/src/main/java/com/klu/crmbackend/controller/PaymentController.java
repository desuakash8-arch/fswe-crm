package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api")
public class PaymentController {
 @ModelAttribute public void headers(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");}
 private final PaymentService payments;private final RazorpayGateway gateway;private final PaymentLinkMailer mailer;
 public PaymentController(PaymentService payments,RazorpayGateway gateway,PaymentLinkMailer mailer){this.payments=payments;this.gateway=gateway;this.mailer=mailer;}
 public record Create(Long dealId){} public record Token(String token){} public record Verification(String token,String razorpay_order_id,String razorpay_payment_id,String razorpay_signature){}
 @GetMapping("/payments/config") public Object config(){return Map.of("configured",gateway.configured(),"mode","TEST","emailEnabled",mailer.enabled(),"emailRecipientOverride",mailer.recipientOverride(),"localLinks",mailer.localLinks());}
 @GetMapping("/invoices") public Object list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="")String search){return payments.list(page,search);}
 @PostMapping("/invoices") public Object create(@RequestBody Create request){if(request.dealId()==null)throw new IllegalArgumentException("Select a won deal");return payments.create(request.dealId());}
 @GetMapping("/invoices/{id}") public Object detail(@PathVariable Long id){return payments.detail(id);}
 @PostMapping("/invoices/{id}/link") public Object link(@PathVariable Long id){return payments.newLink(id);}
 @PostMapping("/invoices/{id}/email") public Object email(@PathVariable Long id,@RequestBody(required=false)Token request){return payments.emailLink(id,request==null?null:request.token());}
 @PostMapping("/invoices/{id}/reconcile") public Object reconcile(@PathVariable Long id){return payments.reconcile(id);}
 @PostMapping("/public/payments/details") public Object details(@RequestBody Token request){return payments.details(request.token());}
 @PostMapping("/public/payments/order") public Object order(@RequestBody Token request){return payments.order(request.token());}
 @PostMapping("/public/payments/status") public Object status(@RequestBody Token request){return payments.status(request.token());}
 @PostMapping("/public/payments/verify") public Object verify(@RequestBody Verification r){return payments.verify(r.token(),r.razorpay_order_id(),r.razorpay_payment_id(),r.razorpay_signature());}
 @PostMapping("/public/payments/webhook") public Object webhook(@RequestBody byte[] body,@RequestHeader(value="X-Razorpay-Signature",required=false)String signature){payments.webhook(body,signature);return Map.of("received",true);}
}
