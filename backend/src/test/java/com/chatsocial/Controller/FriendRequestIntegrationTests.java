package com.chatsocial.Controller;

import com.chatsocial.entity.Friendship;
import com.chatsocial.entity.User;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.FriendRequestRepository;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:friendrequests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class FriendRequestIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired FriendRequestRepository requests;
    @Autowired FriendshipRepository friendships;
    @MockitoBean JavaMailSender mailSender;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        requests.deleteAll();
        friendships.deleteAll();
        users.deleteAll();
        sender = users.saveAndFlush(new User(null, "sender@example.com", "hash", "sender", UserStatus.ACTIVE, null, null));
        recipient = users.saveAndFlush(new User(null, "recipient@example.com", "hash", "recipient", UserStatus.ACTIVE, null, null));
    }

    @Test
    void sendingUsesAuthenticatedSenderAndCreatesPendingRequestWithoutFriendship() throws Exception {
        JsonNode data = send(sender, "  " + recipient.getId().toUpperCase() + "  ", 201);
        assertEquals(sender.getId(), data.get("requesterId").asText());
        assertEquals(recipient.getId(), data.get("addresseeId").asText());
        assertEquals("PENDING", data.get("status").asText());
        assertNotNull(data.get("createdAt"));
        assertNotNull(data.get("updatedAt"));
        assertEquals(1, requests.count());
        assertEquals(0, friendships.count());
        mvc.perform(get("/api/users/{id}/profile", sender.getId())
                        .with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("INCOMING_PENDING"));
        mvc.perform(get("/api/users/{id}/profile", recipient.getId())
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("OUTGOING_PENDING"));
    }

    @Test
    void duplicateAndReversePendingRequestsAreConflicts() throws Exception {
        send(sender, recipient.getId(), 201);
        send(sender, recipient.getId(), 409);
        send(recipient, sender.getId(), 409);
        assertEquals(1, requests.count());
        assertEquals(FriendshipStatus.PENDING, requests.findAll().get(0).getStatus());
        assertEquals(0, friendships.count());
    }

    @Test
    void alreadyFriendsCannotSendEvenWhenOldInvitationWasAccepted() throws Exception {
        send(sender, recipient.getId(), 201);
        var invitation = requests.findAll().get(0);
        invitation.setStatus(FriendshipStatus.ACCEPTED);
        requests.saveAndFlush(invitation);
        friendships.saveAndFlush(new Friendship(recipient, sender));
        send(sender, recipient.getId(), 409);
        send(recipient, sender.getId(), 409);
        assertEquals(FriendshipStatus.ACCEPTED, requests.findAll().get(0).getStatus());
        mvc.perform(get("/api/users/{id}/profile", recipient.getId())
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("FRIENDS"));
    }

    @Test
    void concludedRequestCanBeReusedInEitherDirectionWhenNotFriends() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        var invitation = requests.findById(id).orElseThrow();
        invitation.setStatus(FriendshipStatus.REJECTED);
        requests.saveAndFlush(invitation);
        mvc.perform(get("/api/users/{id}/profile", recipient.getId())
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("NONE"));
        JsonNode reopened = send(recipient, sender.getId(), 201);
        assertEquals(id, reopened.get("id").asText());
        assertEquals(recipient.getId(), reopened.get("requesterId").asText());
        assertEquals(sender.getId(), reopened.get("addresseeId").asText());
        invitation = requests.findById(id).orElseThrow();
        invitation.setStatus(FriendshipStatus.ACCEPTED);
        requests.saveAndFlush(invitation);
        assertEquals(id, send(sender, recipient.getId(), 201).get("id").asText());
        assertEquals(1, requests.count());
    }

    @Test
    void requiresAuthenticationAndRejectsSelfMalformedAndUnavailableAccounts() throws Exception {
        mvc.perform(post("/api/friend-requests").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + recipient.getId() + "\"}")).andExpect(status().isUnauthorized());
        send(sender, sender.getId(), 400);
        send(sender, UUID.randomUUID().toString(), 404);
        for (String body : new String[]{"{}", "{\"userId\":null}", "{\"userId\":\"\"}", "{\"userId\":\"invalid\"}"}) {
            mvc.perform(post("/api/friend-requests").contentType(MediaType.APPLICATION_JSON).content(body)
                            .with(jwt().jwt(token -> token.subject(sender.getId()))))
                    .andExpect(status().isBadRequest());
        }
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.DELETED, UserStatus.PENDING_VERIFICATION}) {
            recipient.setStatus(state);
            users.saveAndFlush(recipient);
            send(sender, recipient.getId(), 404);
        }
        recipient.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(recipient);
        sender.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(sender);
        send(sender, recipient.getId(), 401);
        assertEquals(0, requests.count());
    }

    @Test
    void concurrentDuplicateSubmissionsProduceOnePendingRequest() throws Exception {
        assertConcurrentSend(false);
    }

    @Test
    void concurrentOppositeSubmissionsProduceOnePendingRequestWithoutAutoAccept() throws Exception {
        assertConcurrentSend(true);
    }

    private void assertConcurrentSend(boolean reversed) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return sendStatus(sender, recipient.getId()); });
            var second = executor.submit(() -> { start.await(); return sendStatus(reversed ? recipient : sender,
                    reversed ? sender.getId() : recipient.getId()); });
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(results.contains(201), results.toString());
            assertTrue(results.contains(409), results.toString());
            assertEquals(1, requests.count());
            assertEquals(FriendshipStatus.PENDING, requests.findAll().get(0).getStatus());
            assertEquals(0, friendships.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private int sendStatus(User actor, String targetId) throws Exception {
        return mvc.perform(post("/api/friend-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", targetId)))
                        .with(jwt().jwt(token -> token.subject(actor.getId()))))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode send(User actor, String targetId, int expectedStatus) throws Exception {
        String result = mvc.perform(post("/api/friend-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", targetId)))
                        .with(jwt().jwt(token -> token.subject(actor.getId()))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("data");
    }
}
