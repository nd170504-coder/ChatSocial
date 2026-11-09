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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
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
    MockMvc mvc;
    User a, b, stranger;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        a = user(); b = user(); stranger = user();
        friendships.saveAndFlush(new Friendship(a, b));
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
