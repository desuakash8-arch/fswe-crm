package com.klu.crmbackend.controller;
import com.klu.crmbackend.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;public AccountController(AccountService accounts){this.accounts=accounts;}
    public record ResetRequest(String email){} public record Reset(String token,String password){} public record Change(String currentPassword,String password){} public record Active(boolean active){}
    @PostMapping("/auth/forgot-password") public Object forgot(@RequestBody ResetRequest request){accounts.requestReset(request.email());return Map.of("message","If an active account matches, a reset email will be queued. Check your inbox.");}
    @PostMapping("/auth/reset-password") public Object reset(@RequestBody Reset request){accounts.reset(request.token(),request.password());return Map.of("message","Password reset. Please sign in again.");}
    @PostMapping("/account/password") public Object change(@RequestBody Change request){accounts.changePassword(request.currentPassword(),request.password());return Map.of("message","Password changed. Please sign in again.");}
    @PatchMapping("/users/{id}/active") public Object active(@PathVariable Long id,@RequestBody Active body){accounts.active(id,body.active());return Map.of("message",body.active()?"Account activated":"Account deactivated");}
}
