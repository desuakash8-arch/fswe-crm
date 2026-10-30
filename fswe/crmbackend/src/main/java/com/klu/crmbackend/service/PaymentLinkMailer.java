package com.klu.crmbackend.service;

import com.klu.crmbackend.model.PaymentInvoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class PaymentLinkMailer {
    private final EmailQueue queue;
    private final EmailService sender;
    private final String publicUrl;

    public PaymentLinkMailer(EmailQueue queue, EmailService sender,
            @Value("${app.public-url:http://127.0.0.1:5173}") String publicUrl) {
        this.queue=queue;
        this.sender=sender;
        this.publicUrl=publicUrl.trim().replaceAll("/+$", "");
    }

    public record Delivery(boolean queued, String message) {}

    public boolean enabled() { return sender.isEnabled(); }
    public String recipientOverride() { return sender.testRouting()?sender.recipient(""):""; }
    public boolean localLinks() {
        try {
            String host=URI.create(publicUrl).getHost();
            return host!=null && (host.equalsIgnoreCase("localhost") || host.startsWith("127.") || host.equals("[::1]"));
        } catch (IllegalArgumentException ex) { return false; }
    }
    private String customerEmail(PaymentInvoice invoice) {
        // Allow a customer's corrected email to be used on an existing invoice.
        String address=invoice.deal.getCustomer()==null?invoice.customerEmail:invoice.deal.getCustomer().getEmail();
        return Rules.email(address);
    }
    public String unavailableReason(PaymentInvoice invoice) {
        if(!enabled()) return "Email is disabled. Enable email before sending the payment link.";
        try {
            if(customerEmail(invoice)==null) return "Add an email address to this customer, then email the payment link.";
        } catch (IllegalArgumentException ex) { return "Correct this customer's email address before sending the payment link."; }
        try {
            URI url=URI.create(publicUrl);
            if(!("http".equalsIgnoreCase(url.getScheme()) || "https".equalsIgnoreCase(url.getScheme()))
                    || url.getHost()==null || url.getRawUserInfo()!=null || url.getRawQuery()!=null || url.getRawFragment()!=null)
                return "Configure PUBLIC_APP_URL with the frontend's HTTP or HTTPS address.";
        } catch (IllegalArgumentException ex) { return "Configure PUBLIC_APP_URL with the frontend's HTTP or HTTPS address."; }
        return null;
    }
    public void requireReady(PaymentInvoice invoice) {
        String reason=unavailableReason(invoice);
        if(reason!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,reason);
    }
    public Delivery send(PaymentInvoice invoice, String token) {
        requireReady(invoice);
        String recipient=customerEmail(invoice);
        invoice.customerEmail=recipient;
        String reference="INV-"+String.format(Locale.ROOT,"%05d",invoice.id);
        String body="Hello "+invoice.customerName+",\n\n"
            +"Your test invoice is ready for payment.\n\n"
            +"Invoice: "+reference+"\n"
            +"Description: "+invoice.title+"\n"
            +"Amount: INR "+BigDecimal.valueOf(invoice.amountMinor,2).toPlainString()+"\n\n"
            +"Click this link to view your invoice and pay:\n"
            +publicUrl+"/pay#"+token+"\n\n"
            +"Link expires: "+invoice.linkExpiresAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm",Locale.ENGLISH))+" (CRM server time).\n"
            +"No CRM login is required. Open the link and click Pay to continue to Razorpay.\n\n"
            +"Razorpay TEST MODE: this is a demonstration payment. No real money will be charged.\n"
            +(localLinks()?"Local demonstration: open this link on the laptop running the CRM.\n":"")
            +"Keep this payment link private. If you have already paid, the page will show your payment status.\n";
        var result=queue.enqueue(recipient,"Payment link for "+reference+" (Razorpay test mode)",body,
            invoice.deal.getAssignedTo()==null?null:invoice.deal.getAssignedTo().getUserId(),invoice.deal.getDealId());
        return new Delivery(true,result.message());
    }
}
