package com.chatsocial.service;

import com.chatsocial.config.SupabaseProperties;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.exception.ApiException;
import com.chatsocial.utils.ProfileImageValidator.ValidatedImage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseStorageServiceTests {
    private HttpServer server;
    private String baseUrl;
    private SupabaseStorageService storage;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<Received> received = new AtomicReference<>();
    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("apikey"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        storage = new SupabaseStorageService(new SupabaseProperties(baseUrl, "test-secret", "profile-images"),
                HttpClient.newHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void uploadUsesSecretApiKeyRawFileAndUniqueUserScopedPath() {
        byte[] bytes = new byte[]{1, 2, 3};
        var image = storage.upload(USER_ID, ProfileImageType.AVATAR, new ValidatedImage(bytes, "image/png", "png"));
        Received request = received.get();
        assertEquals("POST", request.method());
        assertEquals("test-secret", request.key());
        assertEquals("image/png", request.contentType());
        assertArrayEquals(bytes, request.body());
        assertEquals("/storage/v1/object/profile-images/" + image.path(), request.path());
        assertTrue(image.path().startsWith(USER_ID + "/avatars/"));
        assertEquals(baseUrl + "/storage/v1/object/public/profile-images/" + image.path(), image.url());
    }

    @Test
    void deletionSendsExactObjectPathAndOnlyRecognizesOwnedManagedUrls() throws Exception {
        String path = USER_ID + "/covers/00000000-0000-0000-0000-000000000002.webp";
        String url = baseUrl + "/storage/v1/object/public/profile-images/" + path;
        assertEquals(path, storage.managedPath(url, USER_ID, ProfileImageType.COVER).orElseThrow());
        assertTrue(storage.managedPath(url, USER_ID, ProfileImageType.AVATAR).isEmpty());
        assertTrue(storage.managedPath("https://other.example/" + path, USER_ID, ProfileImageType.COVER).isEmpty());
        storage.delete(path);
        assertEquals("DELETE", received.get().method());
        assertEquals("/storage/v1/object/profile-images", received.get().path());
        var body = new ObjectMapper().readTree(new String(received.get().body(), StandardCharsets.UTF_8));
        assertEquals(path, body.get("prefixes").get(0).asText());
        assertThrows(IllegalArgumentException.class, () -> storage.delete("../another-user/avatar.png"));
    }

    @Test
    void nonSuccessStorageResponsesBecomeSafeGatewayErrors() {
        status.set(403);
        ApiException exception = assertThrows(ApiException.class, () -> storage.upload(USER_ID,
                ProfileImageType.COVER, new ValidatedImage(new byte[]{1}, "image/png", "png")));
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("Supabase Storage request failed", exception.getMessage());
    }

    @Test
    void missingConfigurationDoesNotMakeNetworkRequest() {
        storage = new SupabaseStorageService(new SupabaseProperties("", "", "profile-images"),
                HttpClient.newHttpClient(), new ObjectMapper());
        ApiException exception = assertThrows(ApiException.class, () -> storage.upload(USER_ID,
                ProfileImageType.AVATAR, new ValidatedImage(new byte[]{1}, "image/png", "png")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertNull(received.get());
    }

    private record Received(String method, String path, String key, String contentType, byte[] body) {}
}
