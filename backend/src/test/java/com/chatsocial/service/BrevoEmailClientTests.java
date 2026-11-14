package com.chatsocial.service;

import com.chatsocial.config.BrevoProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrevoEmailClientTests {
    HttpServer server;
    BrevoEmailClient client;
    BrevoProperties properties;
    final ObjectMapper json = new ObjectMapper();
    final AtomicReference<Received> received = new AtomicReference<>();
    final AtomicInteger status = new AtomicInteger(201);
    final AtomicInteger calls = new AtomicInteger();
    record Received(String method, String path, String key, String type, String body) {}

    @BeforeEach void setup() throws Exception {
        properties = new BrevoProperties();
        properties.setApiKey("private-test-key");
        properties.setSenderEmail("sender@example.com");
        properties.setSenderName("ChatSocial");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            received.set(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("api-key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = "{\"messageId\":\"test\",\"detail\":\"private-provider-detail\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new BrevoEmailClient(properties, HttpClient.newHttpClient(), json,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email"));
    }

    @AfterEach void stop() { if (server != null) server.stop(0); Thread.interrupted(); }

    @Test void sendsAuthenticatedJsonWithVerifiedSenderAndHtmlContent() {
        client.send(new BrevoEmailClient.Email("recipient@example.com", "Xác minh tài khoản", "<p>123456</p>", "123456"));
        var request = received.get();
        assertEquals("POST", request.method());
        assertEquals("/v3/smtp/email", request.path());
        assertEquals("private-test-key", request.key());
        assertEquals("application/json", request.type());
        var payload = json.readTree(request.body());
        assertEquals("ChatSocial", payload.path("sender").path("name").asText());
        assertEquals("sender@example.com", payload.path("sender").path("email").asText());
        assertEquals("recipient@example.com", payload.path("to").get(0).path("email").asText());
        assertEquals("Xác minh tài khoản", payload.path("subject").asText());
        assertEquals("<p>123456</p>", payload.path("htmlContent").asText());
        assertFalse(payload.has("textContent"));
    }

    @Test void sendsTextForPasswordEmails() {
        client.send(new BrevoEmailClient.Email("recipient@example.com", "Đặt lại mật khẩu", null, "Mã của bạn: 654321"));
        var payload = json.readTree(received.get().body());
        assertEquals("Mã của bạn: 654321", payload.path("textContent").asText());
        assertFalse(payload.has("htmlContent"));
    }

    @Test void providerFailuresAreSanitizedAndNotRetriedAutomatically() {
        for (int code : new int[]{401, 403, 429, 500, 302}) {
            status.set(code);
            var error = assertThrows(BrevoEmailClient.DeliveryException.class,
                    () -> client.send(new BrevoEmailClient.Email("recipient@example.com", "Subject", null, "123456")));
            assertEquals("Email provider delivery failed (HTTP " + code + ")", error.getMessage());
            assertNull(error.getCause());
        }
        assertEquals(5, calls.get());
    }

    @Test void timeoutsAndInterruptionsHaveSafeErrorsAndPreserveInterruptFlag() throws Exception {
        var http = mock(HttpClient.class);
        var failing = new BrevoEmailClient(properties, http, json);
        var email = new BrevoEmailClient.Email("recipient@example.com", "Subject", null, "123456");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("private request data"));
        assertEquals("Email provider delivery failed (timeout)",
                assertThrows(BrevoEmailClient.DeliveryException.class, () -> failing.send(email)).getMessage());
        assertFalse(Thread.currentThread().isInterrupted());
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("private request data"));
        assertEquals("Email provider delivery failed (interrupted)",
                assertThrows(BrevoEmailClient.DeliveryException.class, () -> failing.send(email)).getMessage());
        assertTrue(Thread.currentThread().isInterrupted());
        assertEquals("Email[redacted]", email.toString());
    }
}
