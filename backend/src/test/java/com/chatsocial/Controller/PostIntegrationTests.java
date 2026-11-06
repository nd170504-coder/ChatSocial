package com.chatsocial.Controller;

import com.chatsocial.entity.Friendship;
import com.chatsocial.entity.User;
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
        users.deleteAll();
        author = user("author");
        when(storage.newPostPath(anyString(), any())).thenAnswer(invocation ->
                invocation.getArgument(0) + "/posts/" + UUID.randomUUID() + ".png");
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
