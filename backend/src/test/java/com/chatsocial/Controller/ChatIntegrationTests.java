package com.chatsocial.Controller;

import com.chatsocial.dto.chat.*;
import com.chatsocial.entity.*;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.*;
import com.chatsocial.service.ChatService;
import com.chatsocial.service.FriendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mock.web.MockMultipartFile;
import com.chatsocial.service.SupabaseStorageService;
import com.chatsocial.exception.ApiException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class ChatIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ChatService chat;
    @Autowired FriendService friendService;
    @Autowired UserRepository users;
    @Autowired FriendshipRepository friendships;
    @Autowired MessageRepository messages;
    @MockitoBean JavaMailSender mail;
    @MockitoBean SupabaseStorageService storage;
    @MockitoSpyBean MessageMediaRepository media;
    final java.util.Map<String, byte[]> stored = new ConcurrentHashMap<>();
    static final byte[] PNG = new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10, 1};
    MockMvc mvc;
    User a, b, stranger;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        a = user(); b = user(); stranger = user();
        friendships.saveAndFlush(new Friendship(a, b));
        stored.clear();
        when(storage.newPostPath(anyString(), any())).thenAnswer(call -> a.getId() + "/posts/" + UUID.randomUUID() + ".png");
        doAnswer(call -> { stored.put(call.getArgument(0), ((com.chatsocial.utils.ProfileImageValidator.ValidatedImage) call.getArgument(1)).bytes()); return null; }).when(storage).uploadPostImage(anyString(), any());
        when(storage.downloadPostImage(anyString())).thenAnswer(call -> new SupabaseStorageService.DownloadedImage(stored.get(call.getArgument(0)), "image/png"));
        doAnswer(call -> { stored.remove(call.getArgument(0)); return null; }).when(storage).deletePostImage(anyString());
    }

    MockMultipartFile image(byte[] bytes) { return new MockMultipartFile("file", "photo.png", "image/png", bytes); }

    @Test void imagesArePrivateRetryableAndIncludedInHistoryAndReadReceipts() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        UUID key = UUID.randomUUID();
        var result = mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(PNG))
                .param("clientMessageId", key.toString()).with(jwt().jwt(t -> t.subject(a.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.media[0].mediaType").value("IMAGE")).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).get("data");
        String messageId = data.get("id").asText(), url = data.get("media").get(0).get("url").asText();
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(PNG))
                .param("clientMessageId", key.toString()).with(jwt().jwt(t -> t.subject(a.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(messageId));
        verify(storage, times(1)).uploadPostImage(anyString(), any());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(new byte[]{(byte)137,80,78,71,13,10,26,10,2}))
                .param("clientMessageId", key.toString()).with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isConflict());
        mvc.perform(post("/api/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Other\",\"clientMessageId\":\"" + key + "\"}").with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isConflict());
        mvc.perform(get(url).with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isOk())
                .andExpect(content().bytes(PNG)).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(jwt().jwt(t -> t.subject(stranger.getId())))).andExpect(status().isNotFound());
        mvc.perform(get(url.replace(data.get("media").get(0).get("id").asText(), UUID.randomUUID().toString()))
                .with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isNotFound());
        assertEquals(1, chat.get(b.getId(), id).unreadCount());
        assertEquals(1, chat.history(b.getId(), id, null, 20).items().get(0).media().size());
        assertEquals(0, chat.markRead(b.getId(), id, new ReadRequest(messageId)).unreadCount());
        assertTrue(chat.history(a.getId(), id, null, 20).items().get(0).readByRecipient());
        friendService.unfriend(a.getId(), b.getId());
        mvc.perform(get(url).with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isOk());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(PNG)).param("clientMessageId", UUID.randomUUID().toString())
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isForbidden());
        assertEquals(messageId, chat.sendImage(a.getId(), id, key, image(PNG)).id());
    }

    @Test void imageValidationAndMembershipAreCheckedBeforeUploading() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(PNG)).param("clientMessageId", UUID.randomUUID().toString())
                .with(jwt().jwt(t -> t.subject(stranger.getId())))).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image("not png".getBytes())).param("clientMessageId", UUID.randomUUID().toString())
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(new byte[5 * 1024 * 1024 + 1])).param("clientMessageId", UUID.randomUUID().toString())
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(new byte[0])).param("clientMessageId", UUID.randomUUID().toString())
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/conversations/{id}/images", id).file(image(PNG)).param("clientMessageId", "invalid")
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isBadRequest());
        verify(storage, never()).uploadPostImage(anyString(), any());
    }

    @Test void failedUploadsAndFailedDatabaseWritesCleanUpImagesAndRollbackMessages() {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        doThrow(new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Storage offline")).when(storage).uploadPostImage(anyString(), any());
        assertThrows(ApiException.class, () -> chat.sendImage(a.getId(), id, UUID.randomUUID(), image(PNG)));
        verify(storage).deletePostImage(anyString());
        assertTrue(chat.history(a.getId(), id, null, 20).items().isEmpty());
        doAnswer(call -> { stored.put(call.getArgument(0), PNG); return null; }).when(storage).uploadPostImage(anyString(), any());
        doThrow(new IllegalStateException("Simulated database failure")).when(media).saveAndFlush(any(MessageMedia.class));
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> chat.sendImage(a.getId(), id, UUID.randomUUID(), image(PNG)));
        assertTrue(stored.isEmpty());
        assertTrue(chat.history(a.getId(), id, null, 20).items().isEmpty());
    }

    @Test void concurrentImageRetriesCreateOnlyOneMessageAndOneMedia() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        UUID key = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return chat.sendImage(a.getId(), id, key, image(PNG)); });
            var second = pool.submit(() -> { start.await(); return chat.sendImage(a.getId(), id, key, image(PNG)); });
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS).id(), second.get(10, TimeUnit.SECONDS).id());
            assertEquals(1, chat.history(a.getId(), id, null, 20).items().size());
            assertEquals(1, stored.size());
            verify(storage, times(1)).uploadPostImage(anyString(), any());
        } finally { pool.shutdownNow(); }
    }

    @Test void directConversationIsUniqueEvenWhenBothUsersOpenTogether() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())); });
            var second = pool.submit(() -> { start.await(); return chat.openDirect(b.getId(), new OpenDirectRequest(a.getId())); });
            start.countDown();
            String id = first.get(10, TimeUnit.SECONDS).id();
            assertEquals(id, second.get(10, TimeUnit.SECONDS).id());
            mvc.perform(post("/api/conversations/direct").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"" + b.getId() + "\"}").with(jwt().jwt(t -> t.subject(a.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
            mvc.perform(get("/api/conversations").with(jwt().jwt(t -> t.subject(a.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        } finally { pool.shutdownNow(); }
    }

    @Test void retriesCursorHistoryAndReadWatermarksAreConsistent() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        UUID key = UUID.randomUUID();
        var first = chat.send(a.getId(), id, new SendMessageRequest(" One ", key));
        assertEquals(first.id(), chat.send(a.getId(), id, new SendMessageRequest("One", key)).id());
        var second = chat.send(a.getId(), id, new SendMessageRequest("Two", UUID.randomUUID()));
        var third = chat.send(a.getId(), id, new SendMessageRequest("Three", UUID.randomUUID()));
        mvc.perform(post("/api/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"different\",\"clientMessageId\":\"" + key + "\"}")
                        .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isConflict());
        var page = chat.history(b.getId(), id, null, 2);
        assertEquals(2, page.items().size()); assertTrue(page.hasNext());
        var older = chat.history(b.getId(), id, page.nextBefore(), 2);
        assertEquals(1, older.items().size()); assertFalse(older.hasNext());
        assertEquals(first.id(), older.items().get(0).id());
        assertEquals(3, chat.get(b.getId(), id).unreadCount());
        assertEquals(1, chat.markRead(b.getId(), id, new ReadRequest(second.id())).unreadCount());
        var back = chat.markRead(b.getId(), id, new ReadRequest(first.id()));
        assertEquals(second.id(), back.lastReadMessageId()); assertEquals(1, back.unreadCount());
        assertEquals(0, chat.markRead(b.getId(), id, new ReadRequest(third.id())).unreadCount());
        assertTrue(chat.history(a.getId(), id, null, 20).items().stream().allMatch(MessageResponse::readByRecipient));
        mvc.perform(get("/api/conversations/{id}/messages", id).with(jwt().jwt(t -> t.subject(b.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
        mvc.perform(put("/api/conversations/{id}/read", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastReadMessageId\":\"" + third.id() + "\"}")
                        .with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isOk());
    }

    @Test void outsidersAndForeignCursorsAreRejectedAndUnfriendingKeepsHistory() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        var sent = chat.send(a.getId(), id, new SendMessageRequest("Saved", UUID.randomUUID()));
        for (var request : java.util.List.of(get("/api/conversations/" + id), get("/api/conversations/" + id + "/messages"),
                post("/api/conversations/" + id + "/messages").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hi\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}"),
                put("/api/conversations/" + id + "/read").contentType(MediaType.APPLICATION_JSON).content("{\"lastReadMessageId\":\"" + sent.id() + "\"}"))) {
            mvc.perform(request.with(jwt().jwt(t -> t.subject(stranger.getId())))).andExpect(status().isNotFound());
        }
        friendships.saveAndFlush(new Friendship(a, stranger));
        String otherId = chat.openDirect(a.getId(), new OpenDirectRequest(stranger.getId())).id();
        var foreign = chat.send(a.getId(), otherId, new SendMessageRequest("Other", UUID.randomUUID()));
        mvc.perform(get("/api/conversations/{id}/messages", id).param("before", foreign.id())
                .with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isNotFound());
        friendService.unfriend(a.getId(), b.getId());
        assertFalse(chat.get(a.getId(), id).canSend());
        assertEquals(id, chat.openDirect(b.getId(), new OpenDirectRequest(a.getId())).id());
        assertEquals(1, chat.history(b.getId(), id, null, 20).items().size());
        mvc.perform(post("/api/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Blocked\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isForbidden());
        assertEquals(0, chat.markRead(b.getId(), id, new ReadRequest(sent.id())).unreadCount());
        friendships.saveAndFlush(new Friendship(a, b));
        assertEquals(id, chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id());
        assertTrue(chat.get(a.getId(), id).canSend());
    }

    @Test void validationLoginAndFriendshipAreRequired() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        for (String target : java.util.List.of(a.getId(), stranger.getId())) {
            mvc.perform(post("/api/conversations/direct").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":\"" + target + "\"}").with(jwt().jwt(t -> t.subject(a.getId()))))
                    .andExpect(status().is(target.equals(a.getId()) ? 400 : 403));
        }
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        for (String body : java.util.List.of("{}", "{\"content\":\" \"}", "{\"content\":\"" + "a".repeat(2001) + "\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")) {
            mvc.perform(post("/api/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON).content(body)
                    .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isBadRequest());
        }
        b.setStatus(UserStatus.DISABLED); users.saveAndFlush(b);
        assertFalse(chat.get(a.getId(), id).canSend());
        mvc.perform(post("/api/conversations/{id}/messages", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Blocked\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}")
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isForbidden());
    }

    @Test void concurrentRetriesCreateExactlyOneMessage() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        var input = new SendMessageRequest("Once", UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return chat.send(a.getId(), id, input); });
            var second = pool.submit(() -> { start.await(); return chat.send(a.getId(), id, input); });
            start.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS).id(), second.get(10, TimeUnit.SECONDS).id());
            assertEquals(1, chat.history(a.getId(), id, null, 20).items().size());
        } finally { pool.shutdownNow(); }
    }

    User user() {
        String name = "chat-" + UUID.randomUUID();
        return users.saveAndFlush(new User(null, name + "@example.com", "hash", name, UserStatus.ACTIVE, null, null));
    }
}
