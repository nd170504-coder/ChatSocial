package com.chatsocial.Controller;

import com.chatsocial.dto.auth.LoginRequest;
import com.chatsocial.entity.Post;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.PostPrivacy;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.NotificationRepository;
import com.chatsocial.repository.PostRepository;
import com.chatsocial.repository.UserRepository;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.service.AuthService;
import com.chatsocial.service.PostService;
import com.chatsocial.service.UserSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:notifications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class NotificationIntegrationTests {
    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired PostRepository posts;
    @Autowired NotificationRepository notifications;
    @Autowired AuthService auth;
    @Autowired PostService postService;
    @Autowired UserSessionService sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired SimpUserRegistry registry;
    @Autowired WebApplicationContext context;
    @MockitoBean JavaMailSender mail;
    final ObjectMapper json = new ObjectMapper();

    @Test
    void committedLikeIsDeliveredPrivatelyAndReadApisEnforceRecipient() throws Exception {
        User owner = user(), actor = user();
        var login = login(owner);
        Post post = posts.saveAndFlush(Post.builder().user(owner).content("Public").privacy(PostPrivacy.PUBLIC).build());
        try (Socket socket = connect(login.getAccessToken())) {
            subscribe(socket, owner.getId());
            postService.likePost(actor.getId(), post.getId());
            String frame = socket.next();
            assertTrue(frame.startsWith("MESSAGE"), frame);
            var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
            assertEquals("POST_LIKED", data.get("type").asText());
            assertEquals(actor.getId(), data.get("actorId").asText());
            String id = data.get("id").asText();
            postService.likePost(actor.getId(), post.getId());
            assertEquals(1, notifications.countByRecipientIdAndReadAtIsNull(owner.getId()));
            assertNull(socket.frames.poll(250, TimeUnit.MILLISECONDS));

            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/api/notifications").with(jwt().jwt(token -> token.subject(actor.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
            mvc.perform(get("/api/notifications").with(jwt().jwt(token -> token.subject(owner.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(id));
            mvc.perform(patch("/api/notifications/{id}/read", id).with(jwt().jwt(token -> token.subject(actor.getId()))))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/notifications/{id}/read", id).with(jwt().jwt(token -> token.subject(owner.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.readAt").isNotEmpty());
            mvc.perform(patch("/api/notifications/read-all").with(jwt().jwt(token -> token.subject(owner.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
            postService.deletePost(owner.getId(), post.getId());
            assertFalse(notifications.existsById(id));
        }
    }

    @Test
    void missingTokenAndOtherQueueSubscriptionsAreRejected() throws Exception {
        try (Socket anonymous = new Socket(port)) {
            anonymous.send("CONNECT\naccept-version:1.2\nhost:localhost\n\n");
            assertTrue(anonymous.next().startsWith("ERROR"));
        }
        User user = user();
        try (Socket socket = connect(login(user).getAccessToken())) {
            socket.send("SUBSCRIBE\nid:forbidden\ndestination:/queue/notifications\n\n");
            assertTrue(socket.next().startsWith("ERROR"));
        }
    }

    @Test
    void revokedSocketStopsReceivingButNotificationRemainsStored() throws Exception {
        User owner = user(), actor = user();
        var login = login(owner);
        Post post = posts.saveAndFlush(Post.builder().user(owner).content("Public").privacy(PostPrivacy.PUBLIC).build());
        try (Socket socket = connect(login.getAccessToken())) {
            subscribe(socket, owner.getId());
            sessions.logout(owner.getId(), login.getSessionId());
            postService.likePost(actor.getId(), post.getId());
            assertEquals(1, notifications.countByRecipientIdAndReadAtIsNull(owner.getId()));
            assertNull(socket.frames.poll(500, TimeUnit.MILLISECONDS));
        }
    }

    private User user() {
        String name = "notification-" + UUID.randomUUID();
        return users.saveAndFlush(new User(null, name + "@example.com", passwords.encode("Password123!"), name,
                UserStatus.ACTIVE, null, null));
    }

    private com.chatsocial.dto.auth.AuthResponse login(User user) {
        profiles.saveAndFlush(new UserProfile(null, user, "Notification User", null, null, null, null, null));
        LoginRequest input = new LoginRequest();
        input.setEmail(user.getEmail()); input.setPassword("Password123!");
        return auth.login(input, "notification-test");
    }

    private Socket connect(String token) throws Exception {
        Socket socket = new Socket(port);
        socket.send("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer " + token + "\nheart-beat:0,0\n\n");
        assertTrue(socket.next().startsWith("CONNECTED"));
        return socket;
    }

    private void subscribe(Socket socket, String userId) throws Exception {
        socket.send("SUBSCRIBE\nid:notifications\ndestination:/user/queue/notifications\nack:auto\n\n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            var user = registry.getUser(userId);
            if (user != null && user.getSessions().stream().anyMatch(session -> !session.getSubscriptions().isEmpty())) return;
            Thread.sleep(10);
        }
        fail("Subscription was not registered");
    }

    static class Socket implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final StringBuilder pending = new StringBuilder();
        final WebSocket socket;
        Socket(int port) {
            socket = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), this).join();
        }
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            pending.append(text);
            int end;
            while ((end = pending.indexOf("\0")) >= 0) {
                frames.add(pending.substring(0, end).stripLeading()); pending.delete(0, end + 1);
            }
            socket.request(1); return null;
        }
        void send(String text) { socket.sendText(text + '\0', true).join(); }
        String next() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "No STOMP frame received"); return frame;
        }
        @Override public void close() { socket.abort(); }
    }
}
