package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service @Transactional(noRollbackFor=RazorpayGateway.GatewayFailure.class)
public class PaymentService {
 private final PaymentInvoiceRepository invoices;private final PaymentAttemptRepository attempts;private final DealRepository deals;
 private final RecordAccessService access;private final RazorpayGateway gateway;private final CrmNotifications notifications;private final EmailQueue email;
 private final PaymentLinkMailer linkMailer;
 @PersistenceContext private EntityManager em;
 public PaymentService(PaymentInvoiceRepository invoices,PaymentAttemptRepository attempts,DealRepository deals,RecordAccessService access,RazorpayGateway gateway,CrmNotifications notifications,EmailQueue email,PaymentLinkMailer linkMailer){this.invoices=invoices;this.attempts=attempts;this.deals=deals;this.access=access;this.gateway=gateway;this.notifications=notifications;this.email=email;this.linkMailer=linkMailer;}
 public record InvoiceView(Long id,Long dealId,String title,String customerName,String customerEmail,long amountMinor,String currency,String status,String orderId,String paymentId,LocalDateTime createdAt,LocalDateTime paidAt,LocalDateTime linkExpiresAt){}
 public record AttemptView(String paymentId,String status,String method,long amountMinor,long refundedMinor,LocalDateTime updatedAt){}
 public record Detail(InvoiceView invoice,List<AttemptView> history){}
 public record LinkResult(InvoiceView invoice,String token,PaymentLinkMailer.Delivery delivery){}
 public record Checkout(String keyId,String orderId,long amountMinor,String currency,String status){}
 public record PublicView(Long id,String title,String customerName,long amountMinor,String currency,String status,String paymentId,LocalDateTime paidAt,boolean configured){}
 private InvoiceView view(PaymentInvoice i){return new InvoiceView(i.id,i.deal.getDealId(),i.title,i.customerName,i.customerEmail,i.amountMinor,i.currency,i.status,i.orderId,i.paymentId,i.createdAt,i.paidAt,i.linkExpiresAt);}
 private PublicView publicView(PaymentInvoice i){return new PublicView(i.id,i.title,i.customerName,i.amountMinor,i.currency,i.status,i.paymentId,i.paidAt,gateway.configured());}
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Invoice or payment link not found");}
 private PaymentInvoice owned(Long id){var i=invoices.lock(id).orElseThrow(this::missing);if(!access.canAccess(i.deal.getAssignedTo()))throw missing();return i;}
 private PaymentInvoice linked(String token){if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw missing();var i=invoices.findByTokenHash(AccountService.hash(token)).orElseThrow(this::missing);if(i.linkExpiresAt==null||i.linkExpiresAt.isBefore(LocalDateTime.now()))throw new ResponseStatusException(HttpStatus.GONE,"This payment link expired. Ask your contact for a new link.");return i;}
 private String rotate(PaymentInvoice i){var bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);i.tokenHash=AccountService.hash(token);i.linkExpiresAt=LocalDateTime.now().plusDays(30);return token;}
 public Object list(int page,String search){
  if(page<0||page>100000)throw new IllegalArgumentException("Invalid page");var actor=access.actor();String term=search==null?"":search.trim().toLowerCase(Locale.ROOT);
  var result=invoices.findAll((r,q,cb)->{
   var owner=r.join("deal").join("assignedTo",jakarta.persistence.criteria.JoinType.LEFT);
   var scope="ADMIN".equals(actor.getRole())?cb.conjunction():cb.equal(owner.get("userId"),actor.getUserId());
   if("MANAGER".equals(actor.getRole())&&actor.getCompany()!=null)scope=cb.or(scope,cb.and(cb.equal(owner.get("company").get("companyId"),actor.getCompany().getCompanyId()),cb.equal(owner.get("role"),"SALES")));
   String like="%"+term.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
   return cb.and(scope,cb.or(cb.like(cb.lower(r.get("title")),like,'\\'),cb.like(cb.lower(r.get("customerName")),like,'\\')));
  },PageRequest.of(page,20,Sort.by(org.springframework.data.domain.Sort.Order.desc("id"))));
  return Map.of("content",result.getContent().stream().map(this::view).toList(),"totalElements",result.getTotalElements(),"totalPages",result.getTotalPages());
 }
 public Detail detail(Long id){var i=owned(id);return new Detail(view(i),attempts.findByInvoiceIdOrderByIdDesc(id).stream().map(a->new AttemptView(a.paymentId,a.status,a.method,a.amountMinor,a.refundedMinor,a.updatedAt)).toList());}
 public LinkResult create(Long dealId){
  var d=deals.findById(dealId).orElseThrow(this::missing);access.requireAccess(d.getAssignedTo());em.refresh(d,LockModeType.PESSIMISTIC_WRITE);access.requireAccess(d.getAssignedTo());
  if(!"WON".equals(d.getStage()))throw new IllegalArgumentException("Create an invoice after the deal is won");
  if(d.getCustomer()==null)throw new IllegalArgumentException("Assign a customer to the deal first");
  if(d.getAssignedTo()==null)throw new IllegalArgumentException("Assign an employee to the deal before invoicing");
  if(invoices.existsByDealDealId(dealId))throw new ResponseStatusException(HttpStatus.CONFLICT,"An invoice already exists for this deal. Open Payments to view it.");
  long amount;try{amount=d.getAmount().movePointRight(2).longValueExact();}catch(Exception e){throw new IllegalArgumentException("Invalid invoice amount");}
  if(amount<100||amount>9007199254740991L)throw new IllegalArgumentException("The invoice must be at least INR 1 and within the supported amount range");
  var i=new PaymentInvoice();i.deal=d;i.title=d.getTitle();i.customerName=d.getCustomer().getName();i.customerEmail=d.getCustomer().getEmail();i.amountMinor=amount;i.receipt="crm_"+UUID.randomUUID().toString().replace("-","");
  String token=rotate(i);invoices.saveAndFlush(i);
  notifications.event(d.getAssignedTo(),"PAYMENT","Test invoice created","Invoice #"+i.id+" created for "+i.customerName,"Reason: A won deal was invoiced.\nAmount: INR "+money(i)+"\nMode: Razorpay TEST. No real funds are collected.",d.getDealId());
  String reason=gateway.configured()?linkMailer.unavailableReason(i):"Configure Razorpay test keys before emailing the payment link.";
  var delivery=reason==null?linkMailer.send(i,token):new PaymentLinkMailer.Delivery(false,"Payment email not queued. "+reason);
  return new LinkResult(view(i),token,delivery);
 }
 public LinkResult newLink(Long id){var i=owned(id);String token=rotate(i);return new LinkResult(view(i),token,null);}
 public LinkResult emailLink(Long id,String token){
  var i=owned(id);
  if(i.paidAt!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"This invoice is already settled. A payment request was not emailed.");
  if("PROCESSING".equals(i.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment is processing. Check its status before sending another payment request.");
  gateway.requireConfigured();linkMailer.requireReady(i);
  if(token!=null&&!token.isBlank()){
   if(!token.matches("[A-Za-z0-9_-]{43}")||!Objects.equals(i.tokenHash,AccountService.hash(token))||i.linkExpiresAt==null||i.linkExpiresAt.isBefore(LocalDateTime.now()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"This payment link was replaced or expired. Generate a new link before emailing it.");
  }else token=rotate(i);
  var delivery=linkMailer.send(i,token);
  return new LinkResult(view(i),token,delivery);
 }
 public PublicView details(String token){return publicView(linked(token));}
 public Checkout order(String token){
  var i=linked(token);gateway.requireConfigured();if(i.paidAt!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"This invoice has already been paid");
  if("PROCESSING".equals(i.status))throw new ResponseStatusException(HttpStatus.CONFLICT,"A payment is awaiting capture. Check its status before paying again.");
  if(i.keyId!=null&&!i.keyId.equals(gateway.key()))throw new ResponseStatusException(HttpStatus.CONFLICT,"The Razorpay account changed. Ask the administrator to reconcile this invoice.");
  if(i.orderId==null){
   var found=gateway.findOrder(i.receipt);
   if(found.isEmpty()&&i.orderAttempted)throw new ResponseStatusException(HttpStatus.CONFLICT,"An earlier order request is still unconfirmed. Use Check payment status; do not create another invoice.");
   i.orderAttempted=true;i.keyId=gateway.key();
   RazorpayGateway.Order o;
   try{o=found.orElseGet(()->gateway.createOrder(i.amountMinor,i.receipt));}catch(RazorpayGateway.GatewayFailure e){if(e.definite)i.orderAttempted=false;throw e;}
   if(o.amount()!=i.amountMinor||!i.currency.equals(o.currency())||!i.receipt.equals(o.receipt())||!o.id().matches("order_[A-Za-z0-9]+"))throw new RazorpayGateway.GatewayFailure(false);
   i.orderId=o.id();invoices.saveAndFlush(i);
  }
  return new Checkout(gateway.key(),i.orderId,i.amountMinor,i.currency,i.status);
 }
 public PublicView verify(String token,String orderId,String paymentId,String signature){
  var i=linked(token);sameKey(i);
  if(i.orderId==null||!i.orderId.equals(orderId)||!gateway.verify(i.orderId,paymentId,signature))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment signature verification failed");
  apply(i,gateway.fetchPayment(paymentId));return publicView(i);
 }
 private void sameKey(PaymentInvoice i){gateway.requireConfigured();if(i.keyId!=null&&!gateway.key().equals(i.keyId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Razorpay credentials changed; reconcile with the original account.");}
 private void sync(PaymentInvoice i){
  sameKey(i);
  if(i.orderId==null&&i.orderAttempted){var found=gateway.findOrder(i.receipt);if(found.isPresent()){var o=found.get();if(o.amount()!=i.amountMinor||!o.currency().equals(i.currency))throw new RazorpayGateway.GatewayFailure(false);i.orderId=o.id();}}
  if(i.orderId!=null)for(var p:gateway.payments(i.orderId))apply(i,p);
 }
 public PublicView status(String token){var i=linked(token);sync(i);return publicView(i);}
 public Detail reconcile(Long id){var i=owned(id);sync(i);return detail(id);}
 public void webhook(byte[] body,String signature){
  if(body.length>1000000||!gateway.verifyWebhook(body,signature))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook signature");
  try{var n=tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);var p=n.path("payload").path("payment").path("entity");String order=p.path("order_id").asString("");if(order.isBlank())return;var found=invoices.findByOrderId(order);if(found.isPresent()){sameKey(found.get());apply(found.get(),gateway.fetchPayment(p.path("id").asString()));}}
  catch(ResponseStatusException|IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid webhook payload");}
 }
 private String money(PaymentInvoice i){return java.math.BigDecimal.valueOf(i.amountMinor,2).toPlainString();}
 private void apply(PaymentInvoice i,RazorpayGateway.Payment p){
  if(!Objects.equals(i.orderId,p.orderId())||p.amount()!=i.amountMinor||!i.currency.equals(p.currency())||p.refunded()<0||p.refunded()>p.amount()||p.id()==null||!p.id().matches("pay_[A-Za-z0-9]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Payment does not match this invoice");
  if(!Set.of("created","authorized","captured","failed","refunded").contains(p.status()))return;
  var a=attempts.findByPaymentId(p.id()).orElseGet(PaymentAttempt::new);if(a.invoice!=null&&!a.invoice.id.equals(i.id))throw new IllegalArgumentException("Payment already belongs to another invoice");
  // Never regress a previously captured payment because a stale callback arrived later.
  if(a.status!=null&&Set.of("captured","refunded").contains(a.status)&&!Set.of("captured","refunded").contains(p.status()))return;
  boolean changed=!Objects.equals(a.status,p.status())||a.refundedMinor!=p.refunded();a.invoice=i;a.paymentId=p.id();a.status=p.status();a.method=p.method();a.amountMinor=p.amount();a.refundedMinor=p.refunded();a.updatedAt=LocalDateTime.now();attempts.save(a);
  if((p.status().equals("captured")||p.status().equals("refunded"))){
   boolean first=i.paidAt==null;if(i.paymentId!=null&&!i.paymentId.equals(p.id()))throw new IllegalArgumentException("A different payment already settled this invoice");
   i.paymentId=p.id();if(first)i.paidAt=LocalDateTime.now();String next=p.refunded()==i.amountMinor?"REFUNDED":p.refunded()>0?"PARTIALLY_REFUNDED":"PAID";boolean newStatus=!next.equals(i.status);i.status=next;
   if(first||newStatus){String subject=next.equals("PAID")?"Test payment received":"Test payment refund updated";String body="Invoice #"+i.id+" · "+i.title+"\nAmount: INR "+money(i)+"\nPayment: "+p.id()+"\nStatus: "+next+"\nMode: Razorpay TEST. No real money was transferred.";
    notifications.event(i.deal.getAssignedTo(),"PAYMENT",subject,"Invoice #"+i.id+": "+next,"Reason: Razorpay confirmed the payment status.\n"+body,i.deal.getDealId());
    if(i.customerEmail!=null&&!i.customerEmail.isBlank())email.enqueue(i.customerEmail,subject+" — invoice #"+i.id,body,i.deal.getAssignedTo()==null?null:i.deal.getAssignedTo().getUserId(),i.deal.getDealId());
   }
  }else if(i.paidAt==null){i.status=attempts.findByInvoiceIdOrderByIdDesc(i.id).stream().anyMatch(at->"authorized".equals(at.status))?"PROCESSING":"UNPAID";
   if(changed&&p.status().equals("failed"))notifications.event(i.deal.getAssignedTo(),"PAYMENT","Test payment failed","Payment attempt failed for invoice #"+i.id,"Reason: Razorpay reported a failed attempt. The customer can retry the same invoice.\nPayment: "+p.id(),i.deal.getDealId());
  }
 }
}
