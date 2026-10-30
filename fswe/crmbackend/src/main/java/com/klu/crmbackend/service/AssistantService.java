package com.klu.crmbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class AssistantService {
    public record Request(AssistantContextService.Mode mode, Long customerId, Long dealId, String question) {}
    public record Draft(String subject, String body) {}
    public record Reply(AssistantContextService.Mode mode, String answer, List<GroqAssistantClient.Point> points,
                        Draft draft, List<AssistantContextService.Source> sources, List<String> facts, String scope, String generatedAt) {}
    private final AssistantContextService contexts;
    private final RecordAccessService access;
    private final GroqAssistantClient groq;
    private final Map<Long, long[]> usage = new HashMap<>();
    private final Semaphore slots = new Semaphore(3);
    public AssistantService(AssistantContextService contexts, RecordAccessService access, GroqAssistantClient groq) {
        this.contexts = contexts; this.access = access; this.groq = groq;
    }
    public Map<String, Object> config() { access.actor(); return Map.of("configured", groq.configured(), "provider", "Groq", "model", groq.model()); }
    public Reply ask(Request request) {
        if (request == null || request.mode() == null) throw new IllegalArgumentException("Choose what the assistant should help with");
        if (request.customerId() != null && request.customerId() < 1 || request.dealId() != null && request.dealId() < 1)
            throw new IllegalArgumentException("Select a valid record");
        if (request.question() != null && request.question().length() > 1500) throw new IllegalArgumentException("Keep the question under 1500 characters");
        groq.requireConfigured();
        // Snapshot construction finishes its read transaction before the network request starts.
        var context = contexts.build(request.mode(), request.customerId(), request.dealId());
        take(context.actorId(), System.currentTimeMillis());
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The assistant is busy. Please try again shortly.");
        try {
            var answer = groq.generate(request.mode(), request.question() == null ? "" : request.question().trim(), context);
            Draft draft = request.mode() == AssistantContextService.Mode.EMAIL_DRAFT ? new Draft(answer.draftSubject(), answer.draftBody()) : null;
            if (draft != null && (draft.subject().isBlank() || draft.body().isBlank()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The AI did not finish the email draft. Please try again.");
            return new Reply(request.mode(), answer.answer(), answer.points(), draft, context.sources(), context.facts(), context.scope(), context.generatedAt());
        } finally { slots.release(); }
    }
    synchronized void take(Long id, long now) {
        usage.entrySet().removeIf(entry -> now - entry.getValue()[0] >= 60000);
        if (usage.size() >= 10000 && !usage.containsKey(id)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "The assistant is busy. Please try again shortly.");
        var count = usage.computeIfAbsent(id, ignored -> new long[]{now, 0});
        if (count[1] >= 6) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You have made 6 AI requests this minute. Wait a minute, then try again.");
        count[1]++;
    }
}
