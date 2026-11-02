package com.chatsocial.Controller;

import com.chatsocial.config.JwtProperties;
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
