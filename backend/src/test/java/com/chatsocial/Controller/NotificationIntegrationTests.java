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
    @Autowired com.chatsocial.service.ChatService chat;
    @Autowired com.chatsocial.repository.FriendshipRepository friendships;
    @Autowired com.chatsocial.service.PresenceService presence;
    @Autowired com.chatsocial.service.BlockService blocks;
    @Autowired UserSessionService sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired SimpUserRegistry registry;
    @Autowired WebApplicationContext context;
    @MockitoBean JavaMailSender mail;
    @MockitoBean com.chatsocial.service.SupabaseStorageService storage;
    final ObjectMapper json = new ObjectMapper();

    @Test
    void blockingRefreshesBothChatsWithoutSendingABlockNotification() throws Exception {
        User a = user(), b = user();
        friendships.saveAndFlush(new com.chatsocial.entity.Friendship(a, b));
        String conversationId = chat.openDirect(a.getId(), new com.chatsocial.dto.chat.OpenDirectRequest(b.getId())).id();
        chat.send(a.getId(), conversationId, new com.chatsocial.dto.chat.SendMessageRequest("History", UUID.randomUUID()));
        try (Socket first = connect(login(a).getAccessToken()); Socket second = connect(login(b).getAccessToken())) {
            subscribe(first, a.getId(), "/user/queue/chat"); subscribe(first, a.getId(), "/user/queue/notifications");
            subscribe(second, b.getId(), "/user/queue/chat"); subscribe(second, b.getId(), "/user/queue/notifications");
            assertTrue(presence.getOnlineFriends(a.getId()).onlineUserIds().contains(b.getId()));
            blocks.change(a.getId(), b.getId(), true);
            for (Socket socket : java.util.List.of(first, second)) {
                String frame = socket.next();
                var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                assertEquals("RELATIONSHIP_CHANGED", data.get("type").asText());
                assertEquals(conversationId, data.get("conversationId").asText());
                assertTrue(data.get("message").isNull());
                assertNull(socket.frames.poll(250, TimeUnit.MILLISECONDS));
            }
            assertFalse(chat.get(b.getId(), conversationId).canSend());
            assertTrue(presence.getOnlineFriends(a.getId()).onlineUserIds().isEmpty());
            assertTrue(presence.getOnlineFriends(b.getId()).onlineUserIds().isEmpty());
            blocks.change(a.getId(), b.getId(), false);
            for (Socket socket : java.util.List.of(first, second)) assertTrue(socket.next().contains("RELATIONSHIP_CHANGED"));
            assertFalse(chat.get(a.getId(), conversationId).canSend());
        }
    }

    @Test
    void presenceIsPrivateAndTracksMultipleConnectionsAndRevocation() throws Exception {
        User a = user(), b = user(), stranger = user();
        var aLogin = login(a);
        var bLogin = login(b);
        var strangerLogin = login(stranger);
        var relation = friendships.saveAndFlush(new com.chatsocial.entity.Friendship(a, b));
        try (Socket first = connect(aLogin.getAccessToken());
             Socket second = connect(bLogin.getAccessToken());
             Socket otherTab = connect(bLogin.getAccessToken());
             Socket outsider = connect(strangerLogin.getAccessToken())) {
            subscribe(first, a.getId(), "/user/queue/presence");
            subscribe(second, b.getId(), "/user/queue/presence");
            subscribe(otherTab, b.getId(), "/user/queue/presence");
            subscribe(outsider, stranger.getId(), "/user/queue/presence");
            assertEquals(java.util.List.of(b.getId()), presence.getOnlineFriends(a.getId()).onlineUserIds());
            assertTrue(presence.getOnlineFriends(stranger.getId()).onlineUserIds().isEmpty());
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/api/presence/friends")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/presence/friends").with(jwt().jwt(token -> token.subject(a.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.onlineUserIds[0]").value(b.getId()))
                    .andExpect(jsonPath("$.data.onlineUserIds.length()").value(1));
            // Check actual scheduled WebSocket delivery, without invoking the job ourselves.
            var frame = json.readTree(first.next().split("\n\n", 2)[1]);
            assertEquals("PRESENCE_SNAPSHOT", frame.get("type").asText());
            assertEquals(b.getId(), frame.get("onlineUserIds").get(0).asText());
            var privateFrame = json.readTree(outsider.next().split("\n\n", 2)[1]);
            assertTrue(privateFrame.get("onlineUserIds").isEmpty());
            second.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (registry.getUser(b.getId()) != null && registry.getUser(b.getId()).getSessions().size() > 1
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertNotNull(registry.getUser(b.getId()));
            assertEquals(1, registry.getUser(b.getId()).getSessions().size());
            assertEquals(java.util.List.of(b.getId()), presence.getOnlineFriends(a.getId()).onlineUserIds());
            friendships.delete(relation);
            assertTrue(presence.getOnlineFriends(a.getId()).onlineUserIds().isEmpty());
            friendships.saveAndFlush(new com.chatsocial.entity.Friendship(a, b));
            // A revoked login is offline even if its underlying socket is still open.
            sessions.logout(b.getId(), bLogin.getSessionId());
            assertTrue(presence.getOnlineFriends(a.getId()).onlineUserIds().isEmpty());
        }
    }

    @Test
    void presenceGoesOfflineAfterTheLastConnectionCloses() throws Exception {
        User a = user(), b = user();
        friendships.saveAndFlush(new com.chatsocial.entity.Friendship(a, b));
        try (Socket socket = connect(login(b).getAccessToken())) {
            assertEquals(java.util.List.of(b.getId()), presence.getOnlineFriends(a.getId()).onlineUserIds());
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!presence.getOnlineFriends(a.getId()).onlineUserIds().isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(presence.getOnlineFriends(a.getId()).onlineUserIds().isEmpty());
    }

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
    void chatMessagesAndReadReceiptsReachBothPrivateSessionsOnce() throws Exception {
        User a = user(), b = user();
        friendships.saveAndFlush(new com.chatsocial.entity.Friendship(a, b));
        String conversationId = chat.openDirect(a.getId(), new com.chatsocial.dto.chat.OpenDirectRequest(b.getId())).id();
        var input = new com.chatsocial.dto.chat.SendMessageRequest("Realtime hello", UUID.randomUUID());
        try (Socket first = connect(login(a).getAccessToken()); Socket second = connect(login(b).getAccessToken())) {
            subscribe(first, a.getId(), "/user/queue/chat");
            subscribe(second, b.getId(), "/user/queue/chat");
            var sent = chat.send(a.getId(), conversationId, input);
            for (Socket socket : java.util.List.of(first, second)) {
                String frame = socket.next();
                assertTrue(frame.startsWith("MESSAGE"), frame);
                var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                assertEquals("MESSAGE_CREATED", data.get("type").asText());
                assertEquals(sent.id(), data.get("message").get("id").asText());
            }
            chat.send(a.getId(), conversationId, input);
            assertNull(first.frames.poll(200, TimeUnit.MILLISECONDS));
            chat.markRead(b.getId(), conversationId, new com.chatsocial.dto.chat.ReadRequest(sent.id()));
            for (Socket socket : java.util.List.of(first, second)) {
                String frame = socket.next();
                var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                assertEquals("MESSAGES_READ", data.get("type").asText());
                assertEquals(b.getId(), data.get("read").get("readerId").asText());
            }
            org.mockito.Mockito.when(storage.newPostPath(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(a.getId() + "/posts/" + UUID.randomUUID() + ".png");
            var image = chat.sendImage(a.getId(), conversationId, UUID.randomUUID(),
                    new org.springframework.mock.web.MockMultipartFile("file", "image.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10,1}));
            for (Socket socket : java.util.List.of(first, second)) {
                String frame = socket.next();
                var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                assertEquals("MESSAGE_CREATED", data.get("type").asText());
                assertEquals(image.id(), data.get("message").get("id").asText());
                assertTrue(data.get("message").get("content").isNull());
                assertEquals("IMAGE", data.get("message").get("media").get(0).get("mediaType").asText());
            }
            chat.recall(a.getId(), conversationId, image.id());
            for (Socket socket : java.util.List.of(first, second)) {
                String frame = socket.next();
                var data = json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
                assertEquals("MESSAGE_RECALLED", data.get("type").asText());
                assertEquals(image.id(), data.get("message").get("id").asText());
                assertTrue(data.get("message").get("content").isNull());
                assertTrue(data.get("message").get("media").isEmpty());
                assertFalse(data.get("message").get("deletedAt").isNull());
            }
            chat.recall(a.getId(), conversationId, image.id());
            assertNull(first.frames.poll(200, TimeUnit.MILLISECONDS));
            assertNull(second.frames.poll(200, TimeUnit.MILLISECONDS));
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
        subscribe(socket, userId, "/user/queue/notifications");
    }

    private void subscribe(Socket socket, String userId, String destination) throws Exception {
        socket.send("SUBSCRIBE\nid:private-test-" + destination.hashCode() + "\ndestination:" + destination + "\nack:auto\n\n");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            var user = registry.getUser(userId);
            if (user != null && user.getSessions().stream().anyMatch(session -> session.getSubscriptions().stream().anyMatch(subscription -> destination.equals(subscription.getDestination())))) return;
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
