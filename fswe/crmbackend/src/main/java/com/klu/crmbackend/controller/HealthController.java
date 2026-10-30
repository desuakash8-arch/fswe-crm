package com.klu.crmbackend.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/api/health") public ResponseEntity<?> health(){
        try {jdbc.queryForObject("SELECT 1",Integer.class);return ResponseEntity.ok(Map.of("status","UP"));}
        catch(Exception ex){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}
    }
}
