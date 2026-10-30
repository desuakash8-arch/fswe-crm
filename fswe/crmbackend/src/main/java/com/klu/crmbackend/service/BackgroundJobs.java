package com.klu.crmbackend.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="app.jobs.enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobs {
    private final EmailQueue queue;private final ReminderService reminders;
    public BackgroundJobs(EmailQueue queue,ReminderService reminders) {this.queue=queue;this.reminders=reminders;}
    @Scheduled(fixedDelayString="${app.mail.poll-ms:15000}",initialDelay=15000) public void send() {queue.process();}
    @Scheduled(fixedDelay=60000,initialDelay=30000) public void remind() {reminders.run();}
}
