package com.klu.crmbackend.service;
import com.klu.crmbackend.model.*;
import com.klu.crmbackend.dto.EmailDeliveryResult;
import com.klu.crmbackend.repository.EmailJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
@Service
public class EmailQueue {
    private final EmailJobRepository jobs;private final EmailService sender;
    public EmailQueue(EmailJobRepository jobs,EmailService sender) { this.jobs=jobs;this.sender=sender; }
    @Transactional public EmailDeliveryResult enqueue(String to,String subject,String body,Long owner,Long deal) {
        if(!sender.isEnabled()) return new EmailDeliveryResult(false,"Email is disabled in the backend configuration.");
        var job=new EmailJob();job.recipient=sender.recipient(to);job.subject=sender.testRouting()?"[LOCAL TEST] "+subject:subject;
        if(job.subject.length()>255)job.subject=job.subject.substring(0,255);
        job.body=(sender.testRouting()?"Local test. Original recipient: "+to+"\n\n":"")+body;job.ownerId=owner;job.dealId=deal;
        jobs.save(job);return new EmailDeliveryResult(false,"Email queued for "+job.recipient+". Check Email history for server acceptance or errors.");
    }
    @Transactional public void process() {
        for(var job:jobs.ready(LocalDateTime.now(),PageRequest.of(0,10))) {
            var result=sender.sendDirectEmail(job.recipient,job.subject,job.body);job.attempts++;
            if(result.sent()) {job.state="ACCEPTED";job.acceptedAt=LocalDateTime.now();job.lastError=null;}
            else { job.lastError=result.message();job.state=job.attempts>=3?"FAILED":"PENDING";job.nextAttempt=LocalDateTime.now().plusMinutes(job.attempts*2L); }
        }
    }
}
