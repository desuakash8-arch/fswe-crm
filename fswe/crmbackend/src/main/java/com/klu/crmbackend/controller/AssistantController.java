package com.klu.crmbackend.controller;

import com.klu.crmbackend.service.AssistantService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;
    public AssistantController(AssistantService assistant) { this.assistant = assistant; }
    @ModelAttribute public void headers(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @GetMapping("/config") public Map<String, Object> config() { return assistant.config(); }
    @PostMapping("/ask") public AssistantService.Reply ask(@RequestBody AssistantService.Request request) { return assistant.ask(request); }
}
