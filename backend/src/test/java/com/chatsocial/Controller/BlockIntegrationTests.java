package com.chatsocial.Controller;

import com.chatsocial.dto.chat.*;
import com.chatsocial.dto.friend.SendFriendRequest;
import com.chatsocial.entity.*;
import com.chatsocial.enums.*;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import com.chatsocial.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:blocks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class BlockIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired BlockService blocks;
    @Autowired BlockPolicy policy;
    @Autowired UserBlockRepository blockRows;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired FriendshipRepository friendships;
    @Autowired FriendRequestRepository requests;
    @Autowired FriendRequestService friendRequests;
    @Autowired ChatService chat;
    @Autowired PostService postService;
    @Autowired PostRepository posts;
    @Autowired PostMediaRepository postMedia;
    @Autowired CommentRepository comments;
    @Autowired PostLikeRepository likes;
    @Autowired NotificationService notifications;
    @MockitoBean JavaMailSender mail;
    @MockitoBean SupabaseStorageService storage;
    MockMvc mvc;
    User a, b, c;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        a = user(); b = user(); c = user();
        friendships.saveAndFlush(new Friendship(a, b));
    }
    User user() {
        String name = "block_" + UUID.randomUUID().toString().replace("-", "");
        User user = users.saveAndFlush(new User(null, name + "@example.com", "hash", name, UserStatus.ACTIVE, null, null));
        profiles.saveAndFlush(new UserProfile(null, user, name, null, null, null, null, null));
        return user;
    }

    @Test void blockAndUnblockAreDirectionalIdempotentAndDoNotRestoreFriendship() throws Exception {
        mvc.perform(get("/api/blocks")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/blocks/{id}", a.getId()).with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/blocks/{id}", b.getId()).with(jwt().jwt(t -> t.subject(a.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.blockedByMe").value(true));
        blocks.change(a.getId(), b.getId(), true);
        assertEquals(1, blockRows.countBetween(a.getId(), b.getId()));
        assertFalse(friendships.areFriends(a.getId(), b.getId()));
        mvc.perform(get("/api/blocks").param("size", "1").with(jwt().jwt(t -> t.subject(a.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].userId").value(b.getId()));
        mvc.perform(get("/api/blocks").with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        var bStatus = blocks.status(b.getId(), a.getId());
        assertFalse(bStatus.blockedByMe()); assertTrue(bStatus.interactionBlocked());
        blocks.change(b.getId(), a.getId(), true);
        blocks.change(a.getId(), b.getId(), false);
        assertTrue(policy.between(a.getId(), b.getId()));
        assertFalse(blocks.status(a.getId(), b.getId()).blockedByMe());
        assertTrue(blocks.status(b.getId(), a.getId()).blockedByMe());
        blocks.change(b.getId(), a.getId(), false);
        blocks.change(b.getId(), a.getId(), false);
        assertFalse(policy.between(a.getId(), b.getId()));
        assertFalse(friendships.areFriends(a.getId(), b.getId()));
        assertFalse(blocks.list(a.getId(), 0, 20).hasNext());
        mvc.perform(get("/api/blocks").param("size", "100").with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isBadRequest());
    }

    @Test void pendingInvitationsAreRemovedAndCannotBeAcceptedOrSentWhileBlocked() throws Exception {
        var pending = friendRequests.send(c.getId(), new SendFriendRequest(a.getId()));
        blocks.change(a.getId(), c.getId(), true);
        assertFalse(requests.existsById(pending.id()));
        assertThrows(ApiException.class, () -> friendRequests.respond(a.getId(), pending.id(), true));
        for (String[] pair : java.util.List.of(new String[]{a.getId(), c.getId()}, new String[]{c.getId(), a.getId()})) {
            mvc.perform(post("/api/friend-requests").contentType("application/json").content("{\"userId\":\"" + pair[1] + "\"}")
                    .with(jwt().jwt(t -> t.subject(pair[0])))).andExpect(status().isNotFound());
        }
        assertTrue(friendRequests.list(a.getId(), true, 0, 20).items().isEmpty());
        blocks.change(a.getId(), c.getId(), false);
        assertTrue(friendRequests.list(a.getId(), true, 0, 20).items().isEmpty());
        assertNotNull(friendRequests.send(c.getId(), new SendFriendRequest(a.getId())));
    }

    @Test void blockedUsersCannotSeeProfilesPostsOrMediaAndOldInteractionsRemainStored() throws Exception {
        Post post = posts.saveAndFlush(Post.builder().user(b).content("Public story").privacy(PostPrivacy.PUBLIC).build());
        var image = postMedia.saveAndFlush(new PostMedia(post, b.getId() + "/posts/" + UUID.randomUUID() + ".png", 0));
        postService.likePost(a.getId(), post.getId());
        var comment = postService.createComment(a.getId(), post.getId(), new com.chatsocial.dto.comment.CreateCommentRequest("Saved comment"));
        blocks.change(a.getId(), b.getId(), true);
        for (String endpoint : java.util.List.of("/api/users/" + b.getId() + "/profile", "/api/users/" + b.getId() + "/posts",
                "/api/users/" + b.getId() + "/friends/count", "/api/post/" + post.getId(), "/api/post/" + post.getId() + "/likes",
                "/api/post/" + post.getId() + "/comments", "/api/post/" + post.getId() + "/comments/count",
                "/api/post/" + post.getId() + "/media/" + image.getId())) {
            mvc.perform(get(endpoint).with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/post/{id}/likes", post.getId()).with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isNotFound());
        mvc.perform(post("/api/post/{id}/comments", post.getId()).contentType("application/json").content("{\"content\":\"Blocked\"}")
                .with(jwt().jwt(t -> t.subject(a.getId())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/{id}/profile", a.getId()).with(jwt().jwt(t -> t.subject(b.getId())))).andExpect(status().isNotFound());
        assertTrue(postService.getFeed(a.getId(), 0, 50).getItems().stream().noneMatch(item -> item.getUserId().equals(b.getId())));
        assertTrue(postService.getPublicPosts(a.getId(), 0, 50).getItems().stream().noneMatch(item -> item.getUserId().equals(b.getId())));
        mvc.perform(get("/api/users/search").param("q", b.getUsername()).with(jwt().jwt(t -> t.subject(a.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertTrue(comments.existsById(comment.id()));
        assertTrue(likes.existsByPostIdAndUserId(post.getId(), a.getId()));
        assertTrue(notifications.list(b.getId(), 0, 50).items().isEmpty());
        assertEquals(0, notifications.unreadCount(b.getId()).unreadCount());
        verify(storage, never()).downloadPostImage(anyString());
        blocks.change(a.getId(), b.getId(), false);
        assertEquals(post.getId(), postService.getPostById(a.getId(), post.getId()).getId());
    }

    @Test void chatHistoryIsPreservedButNewTextAndImagesAreBlockedInBothDirections() throws Exception {
        String id = chat.openDirect(a.getId(), new OpenDirectRequest(b.getId())).id();
        var old = chat.send(a.getId(), id, new SendMessageRequest("Saved history", UUID.randomUUID()));
        blocks.change(a.getId(), b.getId(), true);
        assertFalse(chat.get(a.getId(), id).canSend()); assertFalse(chat.get(b.getId(), id).canSend());
        assertEquals(old.id(), chat.history(b.getId(), id, null, 20).items().get(0).id());
        assertEquals(id, chat.openDirect(b.getId(), new OpenDirectRequest(a.getId())).id());
        for (User actor : java.util.List.of(a, b)) {
            mvc.perform(post("/api/conversations/{id}/messages", id).contentType("application/json")
                    .content("{\"content\":\"No\",\"clientMessageId\":\"" + UUID.randomUUID() + "\"}").with(jwt().jwt(t -> t.subject(actor.getId())))).andExpect(status().isForbidden());
            mvc.perform(multipart("/api/conversations/{id}/images", id).file(new MockMultipartFile("file", "test.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10}))
                    .param("clientMessageId", UUID.randomUUID().toString()).with(jwt().jwt(t -> t.subject(actor.getId())))).andExpect(status().isForbidden());
        }
        verify(storage, never()).uploadPostImage(anyString(), any());
        assertNotNull(chat.recall(a.getId(), id, old.id()).deletedAt());
        blocks.change(a.getId(), b.getId(), false);
        assertFalse(chat.get(a.getId(), id).canSend());
    }

    @Test void concurrentBlockRetriesAreSafe() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return blocks.change(a.getId(), b.getId(), true); });
            var second = pool.submit(() -> { start.await(); return blocks.change(a.getId(), b.getId(), true); });
            start.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS).blockedByMe());
            assertTrue(second.get(10, TimeUnit.SECONDS).blockedByMe());
            assertEquals(1, blockRows.countBetween(a.getId(), b.getId()));
            assertFalse(friendships.areFriends(a.getId(), b.getId()));
        } finally { pool.shutdownNow(); }
    }
}
