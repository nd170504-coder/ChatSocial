package com.chatsocial.service;

import com.chatsocial.config.BrevoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BrevoEmailClient {
    private final BrevoProperties properties;
    private final HttpClient client;
    private final ObjectMapper json;
    private final URI endpoint;

    @Autowired
    public BrevoEmailClient(BrevoProperties properties, HttpClient client, ObjectMapper json) {
        this(properties, client, json, URI.create("https://api.brevo.com/v3/smtp/email"));
    }

    // Separate endpoint only for isolated HTTP contract tests; production always uses HTTPS above.
    BrevoEmailClient(BrevoProperties properties, HttpClient client, ObjectMapper json, URI endpoint) {
        this.properties = properties;
        this.client = client;
        this.json = json;
        this.endpoint = endpoint;
    }

    public record Email(String to, String subject, String htmlContent, String textContent) {
        @Override public String toString() { return "Email[redacted]"; }
    }

    public static class DeliveryException extends RuntimeException {
        public DeliveryException() { super("Email provider delivery failed"); }
        private DeliveryException(String safeReason) { super("Email provider delivery failed (" + safeReason + ")"); }
    }

    public void send(Email email) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("sender", Map.of("name", properties.getSenderName(), "email", properties.getSenderEmail()));
        payload.put("to", List.of(Map.of("email", email.to())));
        payload.put("subject", email.subject());
        if (email.htmlContent() != null) payload.put("htmlContent", email.htmlContent());
        else payload.put("textContent", email.textContent());
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("api-key", properties.getApiKey())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                .build();
        try {
            // Do not retain/log the response body: provider errors can echo email data.
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DeliveryException("HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeliveryException("interrupted");
        } catch (IOException exception) {
            throw new DeliveryException(exception instanceof HttpTimeoutException ? "timeout" : "connection error");
        }
    }
}
