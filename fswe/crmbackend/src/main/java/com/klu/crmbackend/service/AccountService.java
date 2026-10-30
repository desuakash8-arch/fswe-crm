package com.klu.crmbackend.service;
import com.klu.crmbackend.repository.*;
import com.klu.crmbackend.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
@Service @Transactional
public class AccountService {
    private final com.klu.crmbackend.service.CrmNotifications events;

    private final UserRepository users;private final PasswordResetRepository resets;private final PasswordEncoder encoder;private final EmailQueue queue;private final RecordAccessService access;
    @Value("${app.public-url:http://127.0.0.1:5173}") private String publicUrl;
    public AccountService(UserRepository users,PasswordResetRepository resets,PasswordEncoder encoder,EmailQueue queue,RecordAccessService access, com.klu.crmbackend.service.CrmNotifications events) {
        this.events=events;this.users=users;this.resets=resets;this.encoder=encoder;this.queue=queue;this.access=access;}
    public static String hash(String token) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public void requestReset(String email) {
        if(email==null)return;var user=users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);if(user==null || !user.getActive())return;
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var reset=new PasswordReset();reset.tokenHash=hash(token);reset.userId=user.getUserId();reset.expiresAt=LocalDateTime.now().plusMinutes(30);resets.save(reset);
        queue.enqueue(user.getEmail(),"Reset your CRM password","Open this link within 30 minutes:\n"+publicUrl+"/reset-password#"+token+"\n\nIgnore this message if you did not request a reset.",user.getUserId(),null);
    }
    public void reset(String token,String password) {
        Rules.password(password);if(token==null || token.length()>128)throw new IllegalArgumentException("Invalid or expired reset link");
        var reset=resets.lock(hash(token)).orElseThrow(()->new IllegalArgumentException("Invalid or expired reset link"));
        if(reset.used || reset.expiresAt.isBefore(LocalDateTime.now()))throw new IllegalArgumentException("Invalid or expired reset link");
        var user=users.findById(reset.userId).orElseThrow(()->new IllegalArgumentException("Invalid or expired reset link"));
        if(!user.getActive())throw new IllegalArgumentException("Invalid or expired reset link");
        reset.used=true;change(user,password);
        resets.saveAndFlush(reset);resets.invalidateForUser(user.getUserId());
    }
    public void changePassword(String current,String password) {
        Rules.password(password);var user=access.actor();if(current==null || !encoder.matches(current,user.getPassword()))throw new IllegalArgumentException("Current password is incorrect");change(user,password);
    }
    private void change(User user,String password){var before=events.snapshot(user);user.setPassword(encoder.encode(password));user.setTokenVersion(user.getTokenVersion()+1);users.save(user);resets.invalidateForUser(user.getUserId());events.updated(user,before);}
    public void active(Long id,boolean active) {
        var actor=access.actor();var target=users.findById(id).orElseThrow(()->new IllegalArgumentException("User not found"));
        if(!"ADMIN".equals(actor.getRole()) && !("MANAGER".equals(actor.getRole()) && access.sameCompany(actor,target) && "SALES".equals(target.getRole())))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"You cannot change this account");
        if(actor.getUserId().equals(id))throw new IllegalArgumentException("You cannot deactivate your own account");
        if(target.getActive()==active)return;
        var before=events.snapshot(target);target.setActive(active);target.setTokenVersion(target.getTokenVersion()+1);users.save(target);events.updated(target,before);
    }
}
