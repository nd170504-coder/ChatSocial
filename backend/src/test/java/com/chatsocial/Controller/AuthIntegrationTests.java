package com.chatsocial.Controller;

import com.chatsocial.config.JwtProperties;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.service.SupabaseStorageService;
import com.chatsocial.service.SupabaseStorageService.StoredImage;
import org.springframework.mock.web.MockMultipartFile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import com.chatsocial.entity.UserProfile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired WebApplicationContext context;
    @MockitoSpyBean UserRepository users;
    @MockitoSpyBean UserProfileRepository profiles;
    @MockitoSpyBean SupabaseStorageService storage;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties jwtProperties;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void registerLoginAndMe() throws Exception {
        JsonNode registered = register(" Alice@Example.com ", " Alice ", " password123 ");
        assertEquals("alice@example.com", registered.get("email").asText());
        assertEquals("alice", registered.get("username").asText());
        var user = users.findByEmail("alice@example.com").orElseThrow();
        assertNotEquals(" password123 ", user.getPasswordHash());
        assertTrue(passwords.matches(" password123 ", user.getPasswordHash()));
        assertEquals("Alice Nguyen", profiles.findByUserId(user.getId()).orElseThrow().getFullName());

        JsonNode auth = login(" ALICE@EXAMPLE.COM ", " password123 ", 200);
        assertEquals("Bearer", auth.get("tokenType").asText());
        assertEquals(10800, auth.get("expiresIn").asInt());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        login("alice@example.com", "password123", 401); // Password spaces must be preserved.
    }

    @Test
    void duplicateEmailAndUsernameAreConflicts() throws Exception {
        register("alice@example.com", "alice", "password123");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(" ALICE@example.com ", "bob", "password123")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("bob@example.com", " ALICE ", "password123")))
                .andExpect(status().isConflict());
        assertEquals(1, users.count());
        assertEquals(1, profiles.count());
    }

    @Test
    void databaseUniquenessStillProtectsAgainstStalePrechecks() throws Exception {
        register("alice@example.com", "alice", "password123");
        doReturn(false).when(users).existsByEmail("alice@example.com");
        doReturn(false).when(users).existsByUsername("bob");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("alice@example.com", "bob", "password123")))
                .andExpect(status().isConflict());
        assertEquals(1, users.count());
        assertEquals(1, profiles.count());
    }

    @Test
    void profileFailureRollsBackUserAndDoesNotExposeDatabaseError() throws Exception {
        doThrow(new DataIntegrityViolationException("private database detail"))
                .when(profiles).saveAndFlush(any(UserProfile.class));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("alice@example.com", "alice", "password123")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
        assertEquals(0, users.count());
        assertEquals(0, profiles.count());
    }

    @Test
    void invalidAndMalformedRequestsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("invalid", "a!", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.username").exists()).andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("alice@example.com", "alice", "ấ".repeat(25))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, users.count());
    }

    @Test
    void wrongPasswordAndMissingUserHaveSameError() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode wrong = login("alice@example.com", "incorrect", 401);
        JsonNode missing = login("nobody@example.com", "incorrect", 401);
        assertEquals(wrong.get("detail"), missing.get("detail"));
        login("alice@example.com", "ấ".repeat(25), 401);
    }

    @Test
    void inactiveAccountsCannotLoginOrReuseTokens() throws Exception {
        register("alice@example.com", "alice", "password123");
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        var user = users.findByEmail("alice@example.com").orElseThrow();
        for (UserStatus state : new UserStatus[]{UserStatus.DISABLED, UserStatus.SUSPENDED}) {
            user.setStatus(state);
            users.saveAndFlush(user);
            login("alice@example.com", "password123", 403);
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingMalformedExpiredWrongSignatureAndWrongIssuerTokensAreRejected() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(
                Base64.getDecoder().decode("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=")));
        for (String token : new String[]{"invalid.jwt", token(encoder, id, jwtProperties.issuer(), -120),
                token(otherKey, id, jwtProperties.issuer(), 900), token(encoder, id, "wrong-issuer", 900),
                token(encoder, "missing-user", jwtProperties.issuer(), 900)}) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void profilePatchPreservesOmittedFieldsClearsNullableFieldsAndCannotEditAnotherUser() throws Exception {
        String aliceId = register("alice@example.com", "alice", "password123").get("id").asText();
        String bobId = register("bob@example.com", "bob", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        mvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(aliceId));
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":" Alice Updated ","bio":" Hello ","dateOfBirth":"2000-05-20","gender":"FEMALE"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.data.bio").value("Hello"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2000-05-20"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bio":null,"dateOfBirth":null,"gender":null,"userId":"%s","avatarUrl":"https://untrusted.example/a.jpg"}
                                """.formatted(bobId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.data.bio").isEmpty()).andExpect(jsonPath("$.data.dateOfBirth").isEmpty())
                .andExpect(jsonPath("$.data.gender").isEmpty()).andExpect(jsonPath("$.data.avatarUrl").isEmpty());
        assertEquals("Alice Nguyen", profiles.findByUserId(bobId).orElseThrow().getFullName());
        assertNull(profiles.findByUserId(aliceId).orElseThrow().getAvatarUrl());
    }

    @Test
    void invalidProfileChangesDoNotChangeStoredProfile() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        for (String body : new String[]{"{\"fullName\":null}", "{\"fullName\":\"  \"}",
                "{\"dateOfBirth\":\"2999-01-01\"}", "{\"gender\":\"INVALID\"}",
                json.writeValueAsString(java.util.Map.of("bio", "x".repeat(1001)))}) {
            mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("Alice Nguyen", profiles.findByUserId(id).orElseThrow().getFullName());
    }

    @Test
    void avatarAndCoverAreUploadedForCurrentUserAndOldAvatarIsCleanedUp() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        String avatarPath = id + "/avatars/00000000-0000-0000-0000-000000000001.png";
        String newAvatarPath = id + "/avatars/00000000-0000-0000-0000-000000000002.png";
        String coverPath = id + "/covers/00000000-0000-0000-0000-000000000003.png";
        doReturn(stored(avatarPath), stored(newAvatarPath)).when(storage)
                .upload(eq(id), eq(ProfileImageType.AVATAR), any());
        doReturn(stored(coverPath)).when(storage).upload(eq(id), eq(ProfileImageType.COVER), any());
        doNothing().when(storage).delete(anyString());
        mvc.perform(multipart("/api/users/me/profile/avatar").file(pngFile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(stored(avatarPath).url()));
        mvc.perform(multipart("/api/users/me/profile/cover").file(pngFile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.coverUrl").value(stored(coverPath).url()));
        mvc.perform(multipart("/api/users/me/profile/avatar").file(pngFile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(stored(newAvatarPath).url()))
                .andExpect(jsonPath("$.data.coverUrl").value(stored(coverPath).url()));
        verify(storage).delete(avatarPath);
        verify(storage, never()).delete(coverPath);
        assertEquals(stored(newAvatarPath).url(), profiles.findByUserId(id).orElseThrow().getAvatarUrl());
    }

    @Test
    void invalidImageUploadsNeverReachStorage() throws Exception {
        register("alice@example.com", "alice", "password123");
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        mvc.perform(multipart("/api/users/me/profile/avatar").file(pngFile()))
                .andExpect(status().isUnauthorized());
        for (MockMultipartFile file : new MockMultipartFile[]{
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "wrong.jpg", "image/jpeg", pngFile().getBytes())}) {
            mvc.perform(multipart("/api/users/me/profile/avatar").file(file)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(result -> assertTrue(java.util.Set.of(400, 413, 415)
                            .contains(result.getResponse().getStatus())));
        }
        verify(storage, never()).upload(anyString(), any(), any());
    }

    @Test
    void storageFailurePreservesPreviousProfile() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        doThrow(new com.chatsocial.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                "Supabase Storage request failed")).when(storage).upload(eq(id), any(), any());
        mvc.perform(multipart("/api/users/me/profile/avatar").file(pngFile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway());
        assertNull(profiles.findByUserId(id).orElseThrow().getAvatarUrl());
    }

    @Test
    void databaseFailureAfterUploadRollsBackUrlAndCleansUpNewObject() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        String path = id + "/avatars/00000000-0000-0000-0000-000000000001.png";
        doReturn(stored(path)).when(storage).upload(eq(id), any(), any());
        doNothing().when(storage).delete(path);
        doThrow(new DataIntegrityViolationException("private database detail")).when(profiles).flush();
        mvc.perform(multipart("/api/users/me/profile/avatar").file(pngFile())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());
        assertNull(profiles.findByUserId(id).orElseThrow().getAvatarUrl());
        verify(storage).delete(path);
    }

    private StoredImage stored(String path) {
        return new StoredImage(path, "https://storage.example/storage/v1/object/public/profile-images/" + path);
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jM1kAAAAASUVORK5CYII="));
    }

    @Test
    void swaggerIsPublicAndDocumentsBearerAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ChatSocial API"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.passwordWithinByteLimit").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password").exists())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileRequest.properties.providedFields").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateProfileRequest.properties.fullNameValid").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users/me/profile'].get").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/profile'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/profile/avatar'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/profile/cover'].post.requestBody.content['multipart/form-data']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/users/me'].get.parameters").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200'].content['application/json'].schema['$ref']").exists());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private JsonNode register(String email, String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, username, password)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            result.andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        }
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return expectedStatus == 200 ? body.get("data") : body;
    }

    private String registration(String email, String username, String password) {
        return json.writeValueAsString(java.util.Map.of("email", email, "username", username,
                "password", password, "fullName", " Alice Nguyen "));
    }

    private String token(JwtEncoder signer, String subject, String issuer, long expiresIn) {
        Instant now = Instant.now();
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(subject).issuer(issuer).issuedAt(now.minusSeconds(300))
                        .expiresAt(now.plusSeconds(expiresIn)).build())).getTokenValue();
    }
}
