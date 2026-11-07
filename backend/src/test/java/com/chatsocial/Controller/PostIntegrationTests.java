package com.chatsocial.Controller;

import com.chatsocial.entity.Friendship;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.entity.Post;
import com.chatsocial.enums.PostPrivacy;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.*;
import com.chatsocial.service.SupabaseStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:posts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class PostIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired PostRepository posts;
    @Autowired FriendshipRepository friendships;
    @Autowired CommentRepository comments;
    @Autowired PostLikeRepository likes;
    @MockitoSpyBean PostMediaRepository media;
    @MockitoBean SupabaseStorageService storage;
    @MockitoBean JavaMailSender mailSender;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User author;
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jXioAAAAASUVORK5CYII=");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        comments.deleteAll();
        likes.deleteAll();
        media.deleteAll();
        posts.deleteAll();
        friendships.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
        author = user("author");
        when(storage.newPostPath(anyString(), any())).thenAnswer(invocation ->
                invocation.getArgument(0) + "/posts/" + UUID.randomUUID() + ".png");
    }

    @Test
    void visitorProfileAndPostsFollowActualFriendshipAndProtectPrivateMedia() throws Exception {
        profiles.saveAndFlush(new UserProfile(null, author, "Minh Anh", "/avatar.png", "/cover.png",
                "Hello", java.time.LocalDate.of(2000, 1, 1), com.chatsocial.enums.Gender.OTHER));
        String publicId = create("{\"content\":\"Public\",\"privacy\":\"PUBLIC\"}").get("id").asText();
        JsonNode friendPost = create("{\"content\":\"Friends\",\"privacy\":\"FRIENDS\"}", image());
        create("{\"content\":\"Private\",\"privacy\":\"PRIVATE\"}", image());
        String friendId = friendPost.get("id").asText();
        String mediaUrl = friendPost.get("media").get(0).get("url").asText();
        User viewer = user("visitor");
        String profileUrl = "/api/users/" + author.getId() + "/profile";
        String postsUrl = "/api/users/" + author.getId() + "/posts";

        mvc.perform(get(profileUrl)).andExpect(status().isUnauthorized());
        mvc.perform(get(profileUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("NONE"))
                .andExpect(jsonPath("$.data.fullName").value("Minh Anh"))
                .andExpect(jsonPath("$.data.coverUrl").value("/cover.png"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.data.gender").doesNotExist());
        mvc.perform(get(postsUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(publicId));
        mvc.perform(get("/api/post/{id}", friendId).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(mediaUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());

        friendships.saveAndFlush(new Friendship(viewer, author));
        mvc.perform(get(profileUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("FRIENDS"));
        mvc.perform(get(postsUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.privacy == 'PRIVATE')]").isEmpty());
        when(storage.downloadPostImage(anyString()))
                .thenReturn(new SupabaseStorageService.DownloadedImage(PNG, "image/png"));
        mvc.perform(get(mediaUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk());

        friendships.deleteAll();
        mvc.perform(get(profileUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("NONE"));
        mvc.perform(get(postsUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get(mediaUrl).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(profileUrl).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.relationship").value("SELF"));
        mvc.perform(get(postsUrl).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void visitorProfileRejectsUnavailableUsersAndSupportsMissingOptionalProfile() throws Exception {
        User viewer = user("visitor");
        mvc.perform(get("/api/users/{id}/profile", author.getId())
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("author"));
        mvc.perform(get("/api/users/{id}/profile", UUID.randomUUID().toString())
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.DELETED, UserStatus.PENDING_VERIFICATION}) {
            author.setStatus(state);
            users.saveAndFlush(author);
            mvc.perform(get("/api/users/{id}/profile", author.getId())
                            .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void feedExcludesOwnPostsAndFiltersPrivacyBeforePagination() throws Exception {
        profiles.saveAndFlush(new UserProfile(null, author, "Minh Anh", "/avatar.png", null, null, null, null));
        String publicId = create("{\"content\":\"Public\",\"privacy\":\"PUBLIC\"}").get("id").asText();
        JsonNode friendPost = create("{\"content\":\"Friends\",\"privacy\":\"FRIENDS\"}", image());
        create("{\"content\":\"Private\",\"privacy\":\"PRIVATE\"}");
        User viewer = user("viewer");
        User stranger = user("stranger");
        User secondFriend = user("secondFriend");
        friendships.saveAndFlush(new Friendship(viewer, author));
        friendships.saveAndFlush(new Friendship(secondFriend, viewer));
        Post strangerPublic = posts.saveAndFlush(Post.builder().user(stranger).content("Stranger public")
                .privacy(PostPrivacy.PUBLIC).build());
        Post secondFriendPost = posts.saveAndFlush(Post.builder().user(secondFriend).content("Friend story")
                .privacy(PostPrivacy.FRIENDS).build());
        // Newer ineligible posts must not consume any slots in the page.
        for (PostPrivacy privacy : PostPrivacy.values()) {
            posts.saveAndFlush(Post.builder().user(viewer).content("Own " + privacy).privacy(privacy).build());
        }
        posts.saveAndFlush(Post.builder().user(stranger).content("Stranger friends")
                .privacy(PostPrivacy.FRIENDS).build());
        posts.saveAndFlush(Post.builder().user(stranger).content("Stranger private")
                .privacy(PostPrivacy.PRIVATE).build());
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.DELETED, UserStatus.PENDING_VERIFICATION}) {
            User unavailable = user(state.name().toLowerCase());
            friendships.saveAndFlush(new Friendship(viewer, unavailable));
            for (PostPrivacy privacy : new PostPrivacy[]{PostPrivacy.PUBLIC, PostPrivacy.FRIENDS}) {
                posts.saveAndFlush(Post.builder().user(unavailable).content("Unavailable").privacy(privacy).build());
            }
            unavailable.setStatus(state);
            users.saveAndFlush(unavailable);
        }
        var visibleIds = java.util.Set.of(publicId, friendPost.get("id").asText(),
                strangerPublic.getId(), secondFriendPost.getId());
        var expected = posts.findAll().stream().filter(post -> visibleIds.contains(post.getId()))
                .sorted(java.util.Comparator.comparing(Post::getCreatedAt).reversed()
                        .thenComparing(Post::getId, java.util.Comparator.reverseOrder()))
                .map(Post::getId).toList();
        mvc.perform(get("/api/post/feed").with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[?(@.userId == '" + viewer.getId() + "')]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.privacy == 'PRIVATE')]").isEmpty())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + publicId + "')].authorName").value("Minh Anh"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + publicId + "')].authorAvatarUrl").value("/avatar.png"));
        for (int page = 0; page < 2; page++) {
            mvc.perform(get("/api/post/feed").param("page", String.valueOf(page)).param("size", "2")
                            .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.items[0].id").value(expected.get(page * 2)))
                    .andExpect(jsonPath("$.data.items[1].id").value(expected.get(page * 2 + 1)))
                    .andExpect(jsonPath("$.data.page").value(page))
                    .andExpect(jsonPath("$.data.hasNext").value(page == 0));
        }
        mvc.perform(get("/api/post/feed").param("page", "2").param("size", "2")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mvc.perform(get("/api/users/{id}/posts", viewer.getId())
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
        patchPost(friendPost.get("id").asText(), "{\"privacy\":\"PRIVATE\"}", 200);
        mvc.perform(get("/api/post/feed").with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3));
        friendships.deleteAll();
        mvc.perform(get("/api/post/feed").with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get(friendPost.get("media").get(0).get("url").asText())
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedRequiresLoginAndValidPagination() throws Exception {
        mvc.perform(get("/api/post/feed")).andExpect(status().isUnauthorized());
        for (String[] params : new String[][]{{"page", "-1"}, {"size", "0"}, {"size", "51"}, {"page", "abc"}}) {
            mvc.perform(get("/api/post/feed").param(params[0], params[1])
                            .with(jwt().jwt(token -> token.subject(author.getId()))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/post/feed").with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void publicFeedOnlyReturnsPublicPostsFromActiveAuthorsAndIncludesAuthorDetails() throws Exception {
        profiles.saveAndFlush(new UserProfile(null, author, "Minh Anh", "/avatar.png", null, null, null, null));
        String publicId = create("{\"content\":\"Public A\",\"privacy\":\"PUBLIC\"}", image()).get("id").asText();
        create("{\"content\":\"Friends A\",\"privacy\":\"FRIENDS\"}");
        create("{\"content\":\"Private A\",\"privacy\":\"PRIVATE\"}");
        User viewer = user("viewer");
        Post other = posts.saveAndFlush(Post.builder().user(viewer).content("Public B").privacy(PostPrivacy.PUBLIC).build());
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.DELETED}) {
            User unavailable = user(state.name().toLowerCase());
            posts.saveAndFlush(Post.builder().user(unavailable).content("Hidden").privacy(PostPrivacy.PUBLIC).build());
            unavailable.setStatus(state);
            users.saveAndFlush(unavailable);
        }
        mvc.perform(get("/api/post/public")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/post/public").param("size", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(other.getId()))
                .andExpect(jsonPath("$.data.items[0].authorName").value("viewer"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/post/public").param("page", "1").param("size", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(publicId))
                .andExpect(jsonPath("$.data.items[0].authorName").value("Minh Anh"))
                .andExpect(jsonPath("$.data.items[0].authorAvatarUrl").value("/avatar.png"))
                .andExpect(jsonPath("$.data.items[0].media.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false));
        patchPost(publicId, "{\"privacy\":\"PRIVATE\"}", 200);
        mvc.perform(get("/api/post/public").with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/post/public").param("page", "-1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/post/public").param("size", "51")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageOnlyCreationPersistsOrderedMediaAndIncludesThemInDetailAndList() throws Exception {
        JsonNode created = create("{}", image(), image());
        String id = created.get("id").asText();
        assertTrue(created.get("content").isNull());
        assertEquals("FRIENDS", created.get("privacy").asText());
        assertFalse(created.get("createdAt").isNull());
        assertEquals(2, created.get("media").size());
        assertEquals(0, created.get("media").get(0).get("displayOrder").asInt());
        assertEquals(1, created.get("media").get(1).get("displayOrder").asInt());
        assertTrue(created.get("media").get(0).get("url").asText().startsWith("/api/post/" + id + "/media/"));
        assertEquals(2, media.findByPostIdOrderByDisplayOrderAsc(id).size());

        mvc.perform(get("/api/post/{id}", id).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.media.length()").value(2));
        mvc.perform(get("/api/users/{id}/posts", author.getId())
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].media.length()").value(2));
    }

    @Test
    void textOnlyCreationSupportsBothPrivacyNamesAndRejectsEmptyPosts() throws Exception {
        JsonNode created = create("{\"content\":\"  Hello  \",\"privacy\":\"PUBLIC\"}");
        assertEquals("Hello", created.get("content").asText());
        assertEquals("PUBLIC", created.get("privacy").asText());
        assertEquals(0, created.get("media").size());
        assertEquals("PRIVATE", create("{\"content\":\"Secret\",\"postPrivacy\":\"PRIVATE\"}")
                .get("privacy").asText());
        mvc.perform(multipart("/api/post").file(postPart("{\"content\":\"   \"}"))
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isBadRequest());
        verify(storage, never()).uploadPostImage(anyString(), any());
    }

    @Test
    void invalidImagesAndTooManyImagesAreRejectedBeforeUpload() throws Exception {
        var tooMany = multipart("/api/post").file(postPart("{}"));
        for (int i = 0; i < 6; i++) tooMany.file(image());
        mvc.perform(tooMany.with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/post").file(postPart("{}"))
                        .file(new MockMultipartFile("files", "bad.png", "image/png", new byte[]{1, 2, 3}))
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/post").file(postPart("{}"))
                        .file(new MockMultipartFile("files", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isPayloadTooLarge());
        verify(storage, never()).uploadPostImage(anyString(), any());
        assertEquals(0, posts.count());
    }

    @Test
    void failedMediaInsertRollsBackPostAndCleansUploadedFiles() throws Exception {
        doThrow(new IllegalStateException("simulated insert failure")).when(media).saveAll(anyList());
        mvc.perform(multipart("/api/post").file(postPart("{}")).file(image())
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isInternalServerError());
        assertEquals(0, posts.count());
        assertEquals(0, media.count());
        verify(storage).deletePostImage(anyString());
    }

    @Test
    void failedSecondUploadCleansBothAttemptedPathsWithoutCreatingPost() throws Exception {
        AtomicInteger uploads = new AtomicInteger();
        doAnswer(invocation -> {
            if (uploads.incrementAndGet() == 2) throw new IllegalStateException("simulated upload failure");
            return null;
        }).when(storage).uploadPostImage(anyString(), any());
        mvc.perform(multipart("/api/post").file(postPart("{}")).file(image()).file(image())
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isInternalServerError());
        assertEquals(0, posts.count());
        verify(storage, times(2)).deletePostImage(anyString());
    }

    @Test
    void mediaRequiresPostVisibilityAndCannotBeReadThroughAnotherPost() throws Exception {
        JsonNode created = create("{}", image());
        String id = created.get("id").asText();
        String mediaId = created.get("media").get(0).get("id").asText();
        String url = created.get("media").get(0).get("url").asText();
        User viewer = user("viewer");
        when(storage.downloadPostImage(anyString()))
                .thenReturn(new SupabaseStorageService.DownloadedImage(PNG, "image/png"));

        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
        verify(storage, never()).downloadPostImage(anyString());

        friendships.saveAndFlush(new Friendship(author, viewer));
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(content().bytes(PNG))
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
        patchPost(id, "{\"privacy\":\"PRIVATE\"}", 200);
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk());

        String otherId = create("{\"content\":\"Other\"}").get("id").asText();
        mvc.perform(get("/api/post/{id}/media/{mediaId}", otherId, mediaId)
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isNotFound());
        patchPost(id, "{\"privacy\":\"PUBLIC\"}", 200);
        friendships.deleteAll();
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk());
        author.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(author);
        mvc.perform(get(url).with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchTracksActualFieldsAndOnlyAllowsClearingTextWhenMediaExists() throws Exception {
        String textId = create("{\"content\":\"Hello\"}").get("id").asText();
        patchPost(textId, "{\"content\":\"Updated\"}", 200);
        assertEquals("Updated", posts.findById(textId).orElseThrow().getContent());
        patchPost(textId, "{\"privacy\":\"PRIVATE\"}", 200);
        assertEquals("Updated", posts.findById(textId).orElseThrow().getContent());
        patchPost(textId, "{\"content\":null}", 400);
        patchPost(textId, "{\"contentProvided\":true,\"privacyProvided\":true}", 400);
        String imageId = create("{\"content\":\"Caption\"}", image()).get("id").asText();
        patchPost(imageId, "{\"content\":null}", 200);
        assertNull(posts.findById(imageId).orElseThrow().getContent());
    }

    @Test
    void deletionCleansStorageOnlyAfterDatabaseCommitAndChecksOwnership() throws Exception {
        JsonNode created = create("{}", image());
        String id = created.get("id").asText();
        String path = media.findByPostIdOrderByDisplayOrderAsc(id).get(0).getMediaUrl();
        User stranger = user("stranger");
        mvc.perform(delete("/api/post/{id}", id).with(jwt().jwt(token -> token.subject(stranger.getId()))))
                .andExpect(status().isNotFound());
        verify(storage, never()).deletePostImage(anyString());
        doAnswer(invocation -> {
            assertFalse(posts.existsById(id));
            assertFalse(media.existsByPostId(id));
            return null;
        }).when(storage).deletePostImage(path);
        mvc.perform(delete("/api/post/{id}", id).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isOk());
        verify(storage).deletePostImage(path);
    }

    @Test
    void failedDatabaseDeletionKeepsPostAndDoesNotDeleteStorage() throws Exception {
        String id = create("{}", image()).get("id").asText();
        doThrow(new IllegalStateException("simulated delete failure")).when(media).deleteAllByPostId(id);
        mvc.perform(delete("/api/post/{id}", id).with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isInternalServerError());
        assertTrue(posts.existsById(id));
        assertTrue(media.existsByPostId(id));
        verify(storage, never()).deletePostImage(anyString());
    }

    private User user(String name) {
        return users.saveAndFlush(new User(null, name + "@example.com", "hash", name,
                UserStatus.ACTIVE, null, null));
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("files", "photo.png", "image/png", PNG);
    }

    private MockMultipartFile postPart(String body) {
        return new MockMultipartFile("post", "", "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode create(String body, MockMultipartFile... files) throws Exception {
        var request = multipart("/api/post").file(postPart(body));
        for (var file : files) request.file(file);
        String result = mvc.perform(request.with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("data");
    }

    private void patchPost(String id, String body, int expectedStatus) throws Exception {
        mvc.perform(patch("/api/post/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(token -> token.subject(author.getId()))))
                .andExpect(status().is(expectedStatus));
    }
}
