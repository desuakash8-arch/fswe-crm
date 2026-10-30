package com.klu.crmbackend.controller;

import com.klu.crmbackend.dto.UserDTO;
import com.klu.crmbackend.model.Company;
import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.CompanyRepository;
import com.klu.crmbackend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final com.klu.crmbackend.service.CrmNotifications events;
    private final com.klu.crmbackend.service.UserLifecycle lifecycle;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, CompanyRepository companyRepository,
                          PasswordEncoder passwordEncoder, com.klu.crmbackend.service.CrmNotifications events, com.klu.crmbackend.service.UserLifecycle lifecycle) {
        this.events=events;this.lifecycle=lifecycle;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record UserRequest(String name, String email, String password, String role,
                              Long companyId, Long managerId) {}

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private String roleOf(User user) {
        return user.getRole() == null ? "" : user.getRole().trim().toUpperCase();
    }

    private User loggedIn(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new IllegalStateException("Logged-in user not found."));
    }

    private boolean sameCompany(User a, User b) {
        return a.getCompany() != null && b.getCompany() != null
            && a.getCompany().getCompanyId().equals(b.getCompany().getCompanyId());
    }

    private ResponseEntity<Map<String, String>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", message));
    }

    
@GetMapping
public ResponseEntity<?> getAllUsers(Authentication authentication) {
    User actor = loggedIn(authentication);
    String actorRole = roleOf(actor);

    if ("ADMIN".equals(actorRole)) {
        return ResponseEntity.ok(
            userRepository.findAll()
                .stream()
                .map(UserDTO::fromUser)
                .toList()
        );
    }

    if ("MANAGER".equals(actorRole)) {
        List<User> visible = userRepository.findAll((r,q,cb)->actor.getCompany()==null?cb.disjunction():cb.and(cb.equal(r.get("company").get("companyId"),actor.getCompany().getCompanyId()),r.get("role").in("SALES","MANAGER")));

        return ResponseEntity.ok(
            visible.stream()
                .map(UserDTO::fromUser)
                .toList()
        );
    }

    return forbidden("Only Admins and Managers can view users.");
}


    @GetMapping("/assignable")
    public ResponseEntity<?> getAssignableUsers(Authentication authentication) {
        User actor = loggedIn(authentication);
        return ResponseEntity.ok(userRepository.findAll((r,q,cb)->{
                var own=cb.equal(r.get("userId"),actor.getUserId());
                var scope="ADMIN".equals(roleOf(actor))?cb.conjunction():"MANAGER".equals(roleOf(actor))&&actor.getCompany()!=null?cb.or(own,cb.and(cb.equal(r.get("company").get("companyId"),actor.getCompany().getCompanyId()),cb.equal(r.get("role"),"SALES"))):own;
                return cb.and(scope,cb.isTrue(r.get("active")));
            }).stream()
            .map(UserDTO::fromUser).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id, Authentication authentication) {
        User actor = loggedIn(authentication);
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        User target = found.get();
        if ("ADMIN".equals(roleOf(actor)) || actor.getUserId().equals(target.getUserId())) {
            return ResponseEntity.ok(UserDTO.fromUser(target));
        }
        if ("MANAGER".equals(roleOf(actor)) && sameCompany(actor, target)
                && ("SALES".equals(roleOf(target)) || "MANAGER".equals(roleOf(target)))) {
            return ResponseEntity.ok(UserDTO.fromUser(target));
        }
        return forbidden("You cannot view this user.");
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserRequest request, Authentication authentication) {
        User actor = loggedIn(authentication);
        String actorRole = roleOf(actor);
        if (!"ADMIN".equals(actorRole) && !"MANAGER".equals(actorRole)) {
            return forbidden("Only Admins or Managers can create users.");
        }
        if (request.name() == null || request.name().isBlank() || request.email() == null
                || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            return badRequest("Name, email, and password are required.");
        }
        String email = com.klu.crmbackend.service.Rules.email(request.email());
        if (userRepository.findByEmail(email).isPresent()) return badRequest("Email already registered.");

        String role = request.role() == null || request.role().isBlank()
            ? "SALES" : request.role().trim().toUpperCase();
        if (!List.of("ADMIN", "MANAGER", "SALES").contains(role)) return badRequest("Invalid role.");
        if ("MANAGER".equals(actorRole) && !"SALES".equals(role)) {
            return forbidden("Managers can create Sales users only.");
        }

        com.klu.crmbackend.service.Rules.password(request.password());
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);

        if ("ADMIN".equals(role)) {
            user.setCompany(null);
            user.setManager(null);
        } else {
            Long requestedCompanyId = "MANAGER".equals(actorRole)
                ? (actor.getCompany() == null ? null : actor.getCompany().getCompanyId())
                : request.companyId();
            Company company = requestedCompanyId == null ? null
                : companyRepository.findById(requestedCompanyId).orElse(null);
            if (company == null) return badRequest("Select a valid company.");
            if ("MANAGER".equals(actorRole) && !sameCompany(actor, company)) {
                return forbidden("You can only register Sales users for your own company.");
            }
            user.setCompany(company);
        }

        if ("SALES".equals(role)) {
            Long managerId = "MANAGER".equals(actorRole) ? actor.getUserId() : request.managerId();
            User manager = managerId == null ? null : userRepository.findById(managerId).orElse(null);
            if (manager == null || !"MANAGER".equals(roleOf(manager))) {
                return badRequest("Select a valid Manager.");
            }
            if (!sameCompany(user, manager)) return badRequest("The Manager and Sales user must belong to the same company.");
            user.setManager(manager);
        }

        User saved = lifecycle.save(user,null);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserDTO.fromUser(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserRequest request,
                                        Authentication authentication) {
        User actor = loggedIn(authentication);
        String actorRole = roleOf(actor);
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        if ("MANAGER".equals(actorRole)) {
            if (!sameCompany(actor, target) || !"SALES".equals(roleOf(target)))
                return forbidden("Managers can edit Sales users in their own company only.");
            if (request.role() != null && !"SALES".equals(request.role().trim().toUpperCase()))
                return forbidden("Managers cannot change user roles.");
            if (request.companyId() != null && !request.companyId().equals(actor.getCompany().getCompanyId()))
                return forbidden("Managers cannot move users to another company.");
            if (request.managerId() != null && !request.managerId().equals(actor.getUserId()))
                return forbidden("Sales users must remain assigned to their own Manager.");
        } else if (!"ADMIN".equals(actorRole)) {
            return forbidden("Only Admins and Managers can edit users.");
        }

        var before=events.snapshot(target);
        if (request.name() != null && !request.name().isBlank()) target.setName(request.name().trim());
        if (request.email() != null && !request.email().isBlank()) {
            String email = com.klu.crmbackend.service.Rules.email(request.email());
            Optional<User> duplicate = userRepository.findByEmail(email);
            if (duplicate.isPresent() && !duplicate.get().getUserId().equals(id))
                return badRequest("Email already registered.");
            target.setEmail(email);
        }
        if (request.password() != null && !request.password().isBlank()) {
            com.klu.crmbackend.service.Rules.password(request.password());target.setPassword(passwordEncoder.encode(request.password()));target.setTokenVersion(target.getTokenVersion()+1);
        }

        if ("ADMIN".equals(actorRole)) {
            if (request.role() != null && !request.role().isBlank()) {
                String newRole = request.role().trim().toUpperCase();
                if (!List.of("ADMIN", "MANAGER", "SALES").contains(newRole)) return badRequest("Invalid role.");
                target.setRole(newRole);
            }
            String targetRole = roleOf(target);
            if ("ADMIN".equals(targetRole)) {
                target.setCompany(null);
                target.setManager(null);
            } else {
                if (request.companyId() != null) {
                    Company company = companyRepository.findById(request.companyId()).orElse(null);
                    if (company == null) return badRequest("Select a valid company.");
                    target.setCompany(company);
                }
                if (request.managerId() != null) {
                    User manager = userRepository.findById(request.managerId()).orElse(null);
                    if (manager == null || !"MANAGER".equals(roleOf(manager))) return badRequest("Select a valid Manager.");
                    target.setManager(manager);
                } else if ("MANAGER".equals(targetRole)) {
                    target.setManager(null);
                }
            }
        }

        if ("MANAGER".equals(roleOf(target)) && target.getCompany() == null)
            return badRequest("Managers must belong to a company.");
        if ("SALES".equals(roleOf(target))) {
            if (target.getCompany() == null || target.getManager() == null
                    || !"MANAGER".equals(roleOf(target.getManager())) || !sameCompany(target, target.getManager()))
                return badRequest("Sales users must have a Manager in the same company.");
        }
        return ResponseEntity.ok(UserDTO.fromUser(lifecycle.save(target,before)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        User actor = loggedIn(authentication);
        if (!"ADMIN".equals(roleOf(actor))) return forbidden("Only Admins can delete users.");
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();
        if(actor.getUserId().equals(id))return badRequest("You cannot delete your own account.");
        lifecycle.delete(userRepository.findById(id).orElseThrow());
        return ResponseEntity.noContent().build();
    }

    private boolean sameCompany(User user, Company company) {
        return user.getCompany() != null && company != null
            && user.getCompany().getCompanyId().equals(company.getCompanyId());
    }
}
