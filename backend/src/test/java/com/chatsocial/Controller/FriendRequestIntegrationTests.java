package com.chatsocial.Controller;

import com.chatsocial.entity.Friendship;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.entity.FriendRequest;
import com.chatsocial.entity.Post;
import com.chatsocial.entity.PostMedia;
import com.chatsocial.enums.PostPrivacy;
import com.chatsocial.repository.PostRepository;
import com.chatsocial.repository.PostMediaRepository;
import com.chatsocial.service.SupabaseStorageService;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.FriendRequestRepository;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserRepository;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:friendrequests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class FriendRequestIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired NotificationRepository notifications;
    @MockitoSpyBean FriendRequestRepository requests;
    @Autowired PostRepository posts;
    @Autowired PostMediaRepository media;
    @MockitoBean SupabaseStorageService storage;
    @Autowired FriendshipRepository friendships;
    @MockitoBean JavaMailSender mailSender;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User sender;
    private User recipient;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        notifications.deleteAll();
        requests.deleteAll();
        friendships.deleteAll();
        media.deleteAll();
        posts.deleteAll();
        profiles.deleteAll();
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
    void senderCanCancelAndResendWithoutStaleCancellationRemovingNewRequest() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        mvc.perform(get("/api/users/{id}/profile", recipient.getId())
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.friendRequestId").value(id));
        cancel(sender, id, 200);
        assertEquals(0, requests.count());
        assertEquals(0, friendships.count());
        for (User actor : List.of(sender, recipient)) {
            String targetId = actor.getId().equals(sender.getId()) ? recipient.getId() : sender.getId();
            mvc.perform(get("/api/users/{id}/profile", targetId)
                            .with(jwt().jwt(token -> token.subject(actor.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("NONE"))
                    .andExpect(jsonPath("$.data.friendRequestId").doesNotExist());
        }
        String newId = send(recipient, sender.getId(), 201).get("id").asText();
        assertNotEquals(id, newId);
        cancel(sender, id, 404);
        cancel(sender, newId, 404);
        assertTrue(requests.existsById(newId));
    }

    @Test
    void recipientAndUnrelatedUserCannotCancelAndAuthenticationIsRequired() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        mvc.perform(delete("/api/friend-requests/{id}", id)).andExpect(status().isUnauthorized());
        cancel(recipient, id, 404);
        User stranger = users.saveAndFlush(new User(null, "stranger@example.com", "hash", "stranger", UserStatus.ACTIVE, null, null));
        cancel(stranger, id, 404);
        cancel(sender, UUID.randomUUID().toString(), 404);
        assertEquals(FriendshipStatus.PENDING, requests.findById(id).orElseThrow().getStatus());
    }

    @Test
    void processedRequestsCannotBeCancelledAndFriendshipIsPreserved() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        for (FriendshipStatus state : new FriendshipStatus[]{FriendshipStatus.ACCEPTED, FriendshipStatus.REJECTED}) {
            var invitation = requests.findById(id).orElseThrow();
            invitation.setStatus(state);
            requests.saveAndFlush(invitation);
            cancel(sender, id, 409);
            assertEquals(state, requests.findById(id).orElseThrow().getStatus());
        }
        var invitation = requests.findById(id).orElseThrow();
        invitation.setStatus(FriendshipStatus.PENDING);
        requests.saveAndFlush(invitation);
        friendships.saveAndFlush(new Friendship(sender, recipient));
        cancel(sender, id, 409);
        assertEquals(1, friendships.count());
    }

    @Test
    void senderCanWithdrawFromInactiveRecipientButInactiveSenderCannotMutate() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        sender.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(sender);
        cancel(sender, id, 401);
        assertTrue(requests.existsById(id));
        sender.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(sender);
        recipient.setStatus(UserStatus.DELETED);
        users.saveAndFlush(recipient);
        cancel(sender, id, 200);
        cancel(sender, id, 404);
        assertEquals(0, requests.count());
    }

    @Test
    void concurrentCancellationOnlyRemovesThePendingRequestOnce() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task = () -> {
                start.await();
                return mvc.perform(delete("/api/friend-requests/{id}", id)
                                .with(jwt().jwt(token -> token.subject(sender.getId()))))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            var result = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(result.contains(200), result.toString());
            assertTrue(result.contains(404), result.toString());
            assertEquals(0, requests.count());
            assertEquals(0, friendships.count());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void incomingAndOutgoingListsOnlyContainTheCurrentUsersPendingInvitations() throws Exception {
        profiles.saveAndFlush(new UserProfile(null, recipient, "Người nhận", "/avatar.png", null, null, null, null));
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        mvc.perform(get("/api/friend-requests/outgoing").with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.items[0].user.id").value(recipient.getId()))
                .andExpect(jsonPath("$.data.items[0].user.fullName").value("Người nhận"))
                .andExpect(jsonPath("$.data.items[0].user.avatarUrl").value("/avatar.png"))
                .andExpect(jsonPath("$.data.items[0].user.email").doesNotExist());
        mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].user.id").value(sender.getId()))
                .andExpect(jsonPath("$.data.items[0].user.fullName").value("sender"));
        mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/friend-requests/outgoing").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        User stranger = users.saveAndFlush(new User(null, "stranger@example.com", "hash", "stranger", UserStatus.ACTIVE, null, null));
        mvc.perform(get("/api/friend-requests/incoming").param("userId", recipient.getId())
                        .with(jwt().jwt(token -> token.subject(stranger.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        cancel(sender, id, 200);
        mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void listsExcludeProcessedInvitationsAndUnavailableUsers() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        for (FriendshipStatus state : new FriendshipStatus[]{FriendshipStatus.ACCEPTED, FriendshipStatus.REJECTED}) {
            var invitation = requests.findById(id).orElseThrow();
            invitation.setStatus(state);
            requests.saveAndFlush(invitation);
            mvc.perform(get("/api/friend-requests/outgoing").with(jwt().jwt(token -> token.subject(sender.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
            mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        }
        var invitation = requests.findById(id).orElseThrow();
        invitation.setStatus(FriendshipStatus.PENDING);
        requests.saveAndFlush(invitation);
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.DELETED, UserStatus.PENDING_VERIFICATION}) {
            recipient.setStatus(state);
            users.saveAndFlush(recipient);
            mvc.perform(get("/api/friend-requests/outgoing").with(jwt().jwt(token -> token.subject(sender.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        }
    }

    @Test
    void listsSortByLatestSendTimeIncludingReopenedInvitationsAndPaginate() throws Exception {
        String original = send(sender, recipient.getId(), 201).get("id").asText();
        var invitation = requests.findById(original).orElseThrow();
        invitation.setStatus(FriendshipStatus.REJECTED);
        requests.saveAndFlush(invitation);
        User other = users.saveAndFlush(new User(null, "other@example.com", "hash", "other", UserStatus.ACTIVE, null, null));
        String otherId = send(sender, other.getId(), 201).get("id").asText();
        send(sender, recipient.getId(), 201);
        mvc.perform(get("/api/friend-requests/outgoing").param("size", "1")
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(original))
                .andExpect(jsonPath("$.data.items[0].sentAt").isNotEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/friend-requests/outgoing").param("page", "1").param("size", "1")
                        .with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(otherId))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void listsRequireAuthenticationAndValidPagination() throws Exception {
        for (String direction : new String[]{"incoming", "outgoing"}) {
            String path = "/api/friend-requests/" + direction;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).param("page", "-1").with(jwt().jwt(token -> token.subject(sender.getId()))))
                    .andExpect(status().isBadRequest());
            for (String size : new String[]{"0", "51"}) {
                mvc.perform(get(path).param("size", size).with(jwt().jwt(token -> token.subject(sender.getId()))))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    @Test
    void acceptingCreatesFriendshipRemovesPendingListsAndUnlocksOnlyFriendsPostsAndMedia() throws Exception {
        Post friendsPost = posts.saveAndFlush(Post.builder().user(sender).content("Friends only").privacy(PostPrivacy.FRIENDS).build());
        Post privatePost = posts.saveAndFlush(Post.builder().user(sender).content("Private").privacy(PostPrivacy.PRIVATE).build());
        PostMedia image = media.saveAndFlush(new PostMedia(friendsPost,
                sender.getId() + "/posts/" + UUID.randomUUID() + ".png", 0));
        String imageUrl = "/api/post/" + friendsPost.getId() + "/media/" + image.getId();
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        mvc.perform(get(imageUrl).with(jwt().jwt(token -> token.subject(recipient.getId())))).andExpect(status().isNotFound());
        respond(recipient, id, "accept", 200);
        assertEquals(FriendshipStatus.ACCEPTED, requests.findById(id).orElseThrow().getStatus());
        assertEquals(1, friendships.count());
        assertTrue(friendships.areFriends(sender.getId(), recipient.getId()));
        assertTrue(friendships.areFriends(recipient.getId(), sender.getId()));
        for (User actor : List.of(sender, recipient)) {
            String target = actor.getId().equals(sender.getId()) ? recipient.getId() : sender.getId();
            mvc.perform(get("/api/users/{id}/profile", target).with(jwt().jwt(token -> token.subject(actor.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("FRIENDS"))
                    .andExpect(jsonPath("$.data.friendRequestId").doesNotExist());
        }
        mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/friend-requests/outgoing").with(jwt().jwt(token -> token.subject(sender.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/users/{id}/posts", sender.getId()).with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(friendsPost.getId()));
        mvc.perform(get("/api/post/{id}", privatePost.getId()).with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isNotFound());
        when(storage.downloadPostImage(anyString())).thenReturn(new SupabaseStorageService.DownloadedImage(new byte[]{1}, "image/png"));
        mvc.perform(get(imageUrl).with(jwt().jwt(token -> token.subject(recipient.getId())))).andExpect(status().isOk());
    }

    @Test
    void rejectingKeepsUsersUnrelatedAndAllowsANewInvitation() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        respond(recipient, id, "reject", 200);
        assertEquals(FriendshipStatus.REJECTED, requests.findById(id).orElseThrow().getStatus());
        assertEquals(0, friendships.count());
        mvc.perform(get("/api/users/{id}/profile", sender.getId()).with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("NONE"));
        mvc.perform(get("/api/friend-requests/incoming").with(jwt().jwt(token -> token.subject(recipient.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertEquals(id, send(sender, recipient.getId(), 201).get("id").asText());
    }

    @Test
    void onlyTheAuthenticatedRecipientCanRespondToAPendingRequest() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        User stranger = users.saveAndFlush(new User(null, "stranger@example.com", "hash", "stranger", UserStatus.ACTIVE, null, null));
        for (String action : List.of("accept", "reject")) {
            mvc.perform(post("/api/friend-requests/{id}/{action}", id, action)).andExpect(status().isUnauthorized());
            respond(sender, id, action, 404);
            respond(stranger, id, action, 404);
            respond(recipient, UUID.randomUUID().toString(), action, 404);
        }
        respond(recipient, id, "accept", 200);
        respond(recipient, id, "accept", 409);
        respond(recipient, id, "reject", 409);
        assertEquals(1, friendships.count());
        assertEquals(FriendshipStatus.ACCEPTED, requests.findById(id).orElseThrow().getStatus());
    }

    @Test
    void inactiveAccountsCannotBecomeFriendsButRecipientCanRejectUnavailableSender() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        recipient.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(recipient);
        respond(recipient, id, "accept", 401);
        respond(recipient, id, "reject", 401);
        recipient.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(recipient);
        sender.setStatus(UserStatus.DELETED);
        users.saveAndFlush(sender);
        respond(recipient, id, "accept", 404);
        assertEquals(FriendshipStatus.PENDING, requests.findById(id).orElseThrow().getStatus());
        respond(recipient, id, "reject", 200);
        assertEquals(0, friendships.count());
    }

    @Test
    void failedInvitationUpdateRollsBackFriendshipCreation() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        doThrow(new IllegalStateException("simulated response failure")).when(requests).saveAndFlush(any(FriendRequest.class));
        respond(recipient, id, "accept", 500);
        assertEquals(0, friendships.count());
        assertEquals(FriendshipStatus.PENDING, requests.findById(id).orElseThrow().getStatus());
    }

    @Test
    void concurrentAcceptsCreateOnlyOneFriendship() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> accept = () -> { start.await(); return responseStatus(recipient, id, "accept"); };
            var first = pool.submit(accept);
            var second = pool.submit(accept);
            start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertTrue(results.contains(200), results.toString());
            assertTrue(results.contains(409), results.toString());
            assertEquals(1, friendships.count());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentAcceptAndCancelHaveConsistentOutcome() throws Exception {
        String id = send(sender, recipient.getId(), 201).get("id").asText();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var accepted = pool.submit(() -> { start.await(); return responseStatus(recipient, id, "accept"); });
            var cancelled = pool.submit(() -> {
                start.await();
                return mvc.perform(delete("/api/friend-requests/{id}", id)
                        .with(jwt().jwt(token -> token.subject(sender.getId())))).andReturn().getResponse().getStatus();
            });
            start.countDown();
            int acceptStatus = accepted.get(10, TimeUnit.SECONDS);
            int cancelStatus = cancelled.get(10, TimeUnit.SECONDS);
            if (acceptStatus == 200) {
                assertEquals(409, cancelStatus);
                assertEquals(1, friendships.count());
                assertEquals(FriendshipStatus.ACCEPTED, requests.findById(id).orElseThrow().getStatus());
            } else {
                assertEquals(404, acceptStatus);
                assertEquals(200, cancelStatus);
                assertEquals(0, friendships.count());
                assertFalse(requests.existsById(id));
            }
        } finally { pool.shutdownNow(); }
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

    private int responseStatus(User actor, String id, String action) throws Exception {
        return mvc.perform(post("/api/friend-requests/{id}/{action}", id, action)
                        .with(jwt().jwt(token -> token.subject(actor.getId()))))
                .andReturn().getResponse().getStatus();
    }

    private void respond(User actor, String id, String action, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, responseStatus(actor, id, action));
    }

    private void cancel(User actor, String requestId, int expectedStatus) throws Exception {
        mvc.perform(delete("/api/friend-requests/{id}", requestId)
                        .with(jwt().jwt(token -> token.subject(actor.getId()))))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode send(User actor, String targetId, int expectedStatus) throws Exception {
        String result = mvc.perform(post("/api/friend-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("userId", targetId)))
                        .with(jwt().jwt(token -> token.subject(actor.getId()))))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("data");
    }
}
