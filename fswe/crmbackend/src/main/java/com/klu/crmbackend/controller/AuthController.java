
package com.klu.crmbackend.controller;

import com.klu.crmbackend.model.User;
import com.klu.crmbackend.repository.UserRepository;
import com.klu.crmbackend.service.JwtService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // =====================================================
    // REGISTER
    // =====================================================

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(403).body(Map.of(
                "message",
                "Public registration is disabled. Please ask your company Admin or Manager to create your account."
        ));
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request
    ) {

        if (request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email and password are required"));
        }
        String email = request.email()
                .trim()
                .toLowerCase();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElse(null);

        // User not found
        if (user == null || !user.getActive()) {

            return ResponseEntity
                    .status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "Invalid email or password"
                            )
                    );
        }

        // Check password
        boolean passwordMatches =
                passwordEncoder.matches(
                        request.password(),
                        user.getPassword()
                );

        if (!passwordMatches) {

            return ResponseEntity
                    .status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "Invalid email or password"
                            )
                    );
        }

        // Generate JWT
        String token =
                jwtService.generateToken(
                        user.getUserId(),
                        user.getEmail(),
                        user.getRole(), user.getTokenVersion()
                );

        // Response
        Map<String, Object> response =
                new HashMap<>();

        response.put(
                "token",
                token
        );

        response.put(
                "userId",
                user.getUserId()
        );

        response.put(
                "name",
                user.getName()
        );

        response.put(
                "email",
                user.getEmail()
        );

        response.put("role", user.getRole().trim().toUpperCase());
        response.put("companyId", user.getCompany() == null ? null : user.getCompany().getCompanyId());

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // REGISTER REQUEST
    // =====================================================

    public record RegisterRequest(
            String name,
            String email,
            String password,
               String role
    ) {
    }

    // =====================================================
    // LOGIN REQUEST
    // =====================================================

    public record LoginRequest(
            String email,
            String password
    ) {
    }
}