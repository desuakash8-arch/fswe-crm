package com.klu.crmbackend.controller;

import com.klu.crmbackend.service.DealHealthService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/deals")
public class DealHealthController {
    private final DealHealthService health;
    private final com.klu.crmbackend.service.DealService deals;
    public DealHealthController(DealHealthService health,com.klu.crmbackend.service.DealService deals) { this.health=health;this.deals=deals; }
    @GetMapping("/health") public Object batch(@RequestParam List<Long> ids) { return health.batch(ids); }
    @GetMapping("/{id}/health") public Object one(@PathVariable Long id) { return health.one(id); }
    public record ClosingDate(java.time.LocalDate expectedCloseDate) {}
    @PatchMapping("/{id}/expected-close") public Object updateDate(@PathVariable Long id,@RequestBody ClosingDate body) {
        return deals.changeExpectedCloseDate(id,body.expectedCloseDate());
    }
}
