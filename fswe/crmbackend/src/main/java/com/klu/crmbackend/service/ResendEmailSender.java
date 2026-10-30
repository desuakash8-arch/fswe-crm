package com.klu.crmbackend.service;

import com.klu.crmbackend.dto.EmailDeliveryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** HTTPS email transport for hosts that block SMTP. Secrets and response bodies are never logged. */
@Service
public class ResendEmailSender {
    private final String apiKey;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    public ResendEmailSender(@Value("${app.mail.resend-api-key:}") String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    ResendEmailSender(String apiKey, HttpClient http) {
        this.apiKey = apiKey;
        this.http = http;
    }

    public EmailDeliveryResult send(String from, String to, String subject, String body) {
        if (apiKey == null || apiKey.isBlank())
            return new EmailDeliveryResult(false, "Set RESEND_API_KEY in the backend environment.");
        try {
            var payload = json.writeValueAsString(Map.of("from", from, "to", List.of(to), "subject", subject, "text", body));
            var request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (json.readTree(response.body()).path("id").asString("").isBlank())
                    return new EmailDeliveryResult(false, "The email provider returned no delivery reference. Check its dashboard before retrying.");
                return new EmailDeliveryResult(true, "Email accepted by Resend for " + to + ". Inbox delivery is not yet confirmed.");
            }
            if (response.statusCode() == 401 || response.statusCode() == 403)
                return new EmailDeliveryResult(false, "Resend rejected the request. Check its API key, verified sender, and allowed test recipient.");
            if (response.statusCode() == 429)
                return new EmailDeliveryResult(false, "Resend's sending limit was reached. Check its quota before retrying.");
            return new EmailDeliveryResult(false, "Resend could not accept this email (HTTP " + response.statusCode() + "). Check the provider dashboard.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new EmailDeliveryResult(false, "Email delivery was interrupted. Check the provider dashboard before retrying.");
        } catch (IOException | RuntimeException ex) {
            return new EmailDeliveryResult(false, "Could not confirm email acceptance over HTTPS. Check the provider dashboard before retrying.");
        }
    }
}
