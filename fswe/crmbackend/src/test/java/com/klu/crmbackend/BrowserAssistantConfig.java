package com.klu.crmbackend;

import com.klu.crmbackend.service.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.List;

/** Isolated browser fixture. Never packaged in the production JAR and never calls Groq. */
@TestConfiguration
public class BrowserAssistantConfig {
    @Bean @Primary GroqAssistantClient browserAssistant() {
        return new GroqAssistantClient("", "browser-fixture") {
            @Override public boolean configured() { return true; }
            @Override public void requireConfigured() {}
            @Override public Answer generate(AssistantContextService.Mode mode, String question, AssistantContextService.Context context) {
                var points=context.sources().isEmpty()?List.<Point>of():List.of(new Point("Review the linked record before your next customer conversation.",List.of(context.sources().get(0).id())));
                return new Answer("Browser test answer: "+String.join(". ",context.facts()),points,
                    mode==AssistantContextService.Mode.EMAIL_DRAFT?"Following up on our conversation":"",
                    mode==AssistantContextService.Mode.EMAIL_DRAFT?"Hello,\n\nThank you for your time. Could we discuss the next step?\n\nBest regards,\nYour sales contact":"");
            }
        };
    }
}
