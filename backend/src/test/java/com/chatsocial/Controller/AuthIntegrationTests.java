package com.chatsocial.Controller;

import com.chatsocial.config.JwtProperties;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.service.SupabaseStorageService;
import com.chatsocial.service.SupabaseStorageService.StoredImage;
import com.chatsocial.service.JwtService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import org.mockito.ArgumentCaptor;
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
    @MockitoSpyBean JwtService jwtService;
    @Autowired UserSessionRepository sessions;
    @Autowired UserRefreshTokenRepository refreshTokens;
    @Autowired EmailVerificationRepository verifications;
    @MockitoBean JavaMailSender mailSender;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties jwtProperties;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new java.util.Properties())));
        refreshTokens.deleteAll();
        sessions.deleteAll();
        verifications.deleteAll();
        profiles.deleteAll();
        users.deleteAll();
    }

    @Test
    void registrationSendsMimeEmailAndRequiresVerificationBeforeLogin() throws Exception {
        JsonNode pending = registerPending(" Alice@example.com ", "alice", "password123");
        assertEquals("PENDING_VERIFICATION", pending.get("status").asText());
        login("alice@example.com", "password123", 403);
        assertEquals(0, sessions.count());
        MimeMessage message = lastEmail();
        message.saveChanges();
        assertTrue(message.isMimeType("multipart/*"));
        assertEquals("ChatSocial - Xác minh tài khoản", message.getSubject());
        assertEquals("alice@example.com", message.getAllRecipients()[0].toString());
        String contents = emailContents(message);
        assertTrue(contents.contains("<html"));
        assertTrue(contents.contains("Xác minh địa chỉ email"));
        assertTrue(contents.contains("5 phút"));
        String code = lastOtp();
        var verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        assertEquals(64, verification.getCodeHash().length());
        assertNotEquals(code, verification.getCodeHash());
        verifyEmail(" ALICE@example.com ", code, 200);
        assertEquals(UserStatus.ACTIVE, users.findByEmail("alice@example.com").orElseThrow().getStatus());
        assertNotNull(verifications.findByUserId(pending.get("id").asText()).orElseThrow().getConsumedAt());
        verifyEmail("alice@example.com", code, 400);
        login("alice@example.com", "password123", 200);
    }

    @Test
    void wrongOtpAttemptsPersistAndLockCodeEvenIfCorrectCodeIsLaterProvided() throws Exception {
        JsonNode pending = registerPending("alice@example.com", "alice", "password123");
        String correct = lastOtp();
        String wrong = correct.equals("000000") ? "111111" : "000000";
        for (int count = 1; count <= 5; count++) {
            verifyEmail("alice@example.com", wrong, count == 5 ? 429 : 400);
            assertEquals(count, verifications.findByUserId(pending.get("id").asText()).orElseThrow().getFailedAttempts());
        }
        verifyEmail("alice@example.com", correct, 429);
        assertEquals(UserStatus.PENDING_VERIFICATION, users.findByEmail("alice@example.com").orElseThrow().getStatus());
        var verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        verification.setLastSentAt(Instant.now().minusSeconds(61));
        verifications.saveAndFlush(verification);
        resend("alice@example.com", 202);
        assertEquals(0, verifications.findByUserId(pending.get("id").asText()).orElseThrow().getFailedAttempts());
        verifyEmail("alice@example.com", lastOtp(), 200);
    }

    @Test
    void expiredCodeCannotActivateAccount() throws Exception {
        JsonNode pending = registerPending("alice@example.com", "alice", "password123");
        String code = lastOtp();
        var verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        verification.setExpiresAt(Instant.now().minusSeconds(1));
        verifications.saveAndFlush(verification);
        verifyEmail("alice@example.com", code, 400);
        login("alice@example.com", "password123", 403);
    }

    @Test
    void resendHonorsCooldownHourlyLimitAndInvalidatesPreviousOtp() throws Exception {
        JsonNode pending = registerPending("alice@example.com", "alice", "password123");
        String oldCode = lastOtp();
        resend("alice@example.com", 202);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        var verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        verification.setLastSentAt(Instant.now().minusSeconds(61));
        verifications.saveAndFlush(verification);
        resend("alice@example.com", 202);
        String newCode = lastOtp();
        assertNotEquals(oldCode, newCode);
        verifyEmail("alice@example.com", oldCode, 400);
        verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        verification.setSendCount(5);
        verification.setLastSentAt(Instant.now().minusSeconds(61));
        verifications.saveAndFlush(verification);
        resend("alice@example.com", 202);
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        verification = verifications.findByUserId(pending.get("id").asText()).orElseThrow();
        verification.setSendWindowStartedAt(Instant.now().minusSeconds(3601));
        verifications.saveAndFlush(verification);
        resend("alice@example.com", 202);
        assertEquals(1, verifications.findByUserId(pending.get("id").asText()).orElseThrow().getSendCount());
        verifyEmail("alice@example.com", lastOtp(), 200);
    }

    @Test
    void resendForMissingOrVerifiedOrDisabledAccountDoesNotSendEmail() throws Exception {
        resend("missing@example.com", 202);
        verify(mailSender, never()).send(any(MimeMessage.class));
        register("alice@example.com", "alice", "password123");
        resend("alice@example.com", 202);
        var user = users.findByEmail("alice@example.com").orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        resend("alice@example.com", 202);
        verifyEmail("alice@example.com", lastOtp(), 400);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureKeepsPendingAccountAndCanRecoverThroughResend() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("private SMTP detail"))
                .when(mailSender).send(any(MimeMessage.class));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration("alice@example.com", "alice", "password123")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Unable to send verification email; please request a new code later"));
        var user = users.findByEmail("alice@example.com").orElseThrow();
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
        assertTrue(profiles.findByUserId(user.getId()).isPresent());
        var verification = verifications.findByUserId(user.getId()).orElseThrow();
        verification.setLastSentAt(Instant.now().minusSeconds(61));
        verifications.saveAndFlush(verification);
        doNothing().when(mailSender).send(any(MimeMessage.class));
        resend("alice@example.com", 202);
        verifyEmail("alice@example.com", lastOtp(), 200);
        login("alice@example.com", "password123", 200);
    }

    @Test
    void verificationValidatesInputAndDoesNotAcceptAnotherAccountsCode() throws Exception {
        registerPending("alice@example.com", "alice", "password123");
        String aliceCode = lastOtp();
        JsonNode bob = registerPending("bob@example.com", "bob", "password123");
        // Distinct user-bound hashes even if independently generated codes happened to match.
        assertNotEquals(verifications.findByUserId(users.findByEmail("alice@example.com").orElseThrow().getId()).orElseThrow().getCodeHash(),
                verifications.findByUserId(bob.get("id").asText()).orElseThrow().getCodeHash());
        if (!aliceCode.equals(lastOtp())) verifyEmail("bob@example.com", aliceCode, 400);
        verifyEmail("alice@example.com", "123", 400);
        verifyEmail("alice@example.com", "abcdef", 400);
        verifyEmail("missing@example.com", "123456", 400);
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void simultaneousVerificationOnlyConsumesCodeOnce() throws Exception {
        registerPending("alice@example.com", "alice", "password123");
        String code = lastOtp();
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> task = () -> {
                start.await();
                return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(java.util.Map.of("email", "alice@example.com", "otp", code))))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            var statuses = java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(statuses.contains(200));
            assertTrue(statuses.contains(400));
        } finally {
            pool.shutdownNow();
        }
    }

    private void verifyEmail(String email, String code, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "otp", code))))
                .andExpect(status().is(expectedStatus));
    }

    private void resend(String email, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email))))
                .andExpect(status().is(expectedStatus));
    }

    private MimeMessage lastEmail() {
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private String lastOtp() throws Exception {
        var match = java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(emailContents(lastEmail()));
        assertTrue(match.find(), "MIME email must contain a six-digit code");
        return match.group();
    }

    private String emailContents(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) return text;
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) text.append(emailContents(multipart.getBodyPart(i)));
            return text.toString();
        }
        return "";
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
        String sessionId = login("alice@example.com", "password123", 200).get("sessionId").asText();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(
                Base64.getDecoder().decode("YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=")));
        for (String token : new String[]{"invalid.jwt", token(encoder, id, sessionId, jwtProperties.getIssuer(), -120),
                token(otherKey, id, sessionId, jwtProperties.getIssuer(), 900), token(encoder, id, sessionId, "wrong-issuer", 900),
                token(encoder, "missing-user", sessionId, jwtProperties.getIssuer(), 900),
                token(encoder, id, "missing-session", jwtProperties.getIssuer(), 900),
                token(encoder, id, null, jwtProperties.getIssuer(), 900)}) {
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void loginCreatesSessionStoresOnlyHashAndRecordsUserAgent() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode auth = login("alice@example.com", "password123", 200);
        var session = sessions.findById(auth.get("sessionId").asText()).orElseThrow();
        assertEquals("TestBrowser Desktop", session.getUserAgent());
        assertNull(session.getLastUsedAt());
        assertNull(session.getRevokedAt());
        assertEquals(604800, java.time.Duration.between(session.getCreatedAt(), session.getExpiresAt()).getSeconds());
        var saved = refreshTokens.findAll().get(0);
        assertEquals(64, saved.getTokenHash().length());
        assertNotEquals(auth.get("refreshToken").asText(), saved.getTokenHash());
        assertEquals(43, auth.get("refreshToken").asText().length());
        assertTrue(auth.get("refreshExpiresIn").asLong() > 604700);
    }

    @Test
    void refreshRotatesTokensAndKeepsFixedSessionExpiry() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode first = login("alice@example.com", "password123", 200);
        String id = first.get("sessionId").asText();
        Instant expires = sessions.findById(id).orElseThrow().getExpiresAt();
        JsonNode next = refresh(first.get("refreshToken").asText(), 200);
        assertEquals(id, next.get("sessionId").asText());
        assertNotEquals(first.get("refreshToken"), next.get("refreshToken"));
        assertNotEquals(first.get("accessToken"), next.get("accessToken"));
        assertEquals(expires, sessions.findById(id).orElseThrow().getExpiresAt());
        assertNotNull(sessions.findById(id).orElseThrow().getLastUsedAt());
        assertEquals(2, refreshTokens.count());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + next.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void replayOfEvenOlderRefreshTokenRevokesEntireSessionAndCommitsRevocation() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode first = login("alice@example.com", "password123", 200);
        JsonNode second = refresh(first.get("refreshToken").asText(), 200);
        JsonNode third = refresh(second.get("refreshToken").asText(), 200);
        refresh(first.get("refreshToken").asText(), 401);
        assertNotNull(sessions.findById(first.get("sessionId").asText()).orElseThrow().getRevokedAt());
        refresh(third.get("refreshToken").asText(), 401);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + third.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesCurrentDeviceButLeavesOtherSessionUsable() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode desktop = login("alice@example.com", "password123", 200);
        JsonNode phone = login("alice@example.com", "password123", 200);
        assertNotEquals(desktop.get("sessionId"), phone.get("sessionId"));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + desktop.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + desktop.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        refresh(desktop.get("refreshToken").asText(), 401);
        refresh(phone.get("refreshToken").asText(), 200);
    }

    @Test
    void expiredSessionAndInactiveUserCannotRefresh() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode auth = login("alice@example.com", "password123", 200);
        var session = sessions.findById(auth.get("sessionId").asText()).orElseThrow();
        session.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.saveAndFlush(session);
        refresh(auth.get("refreshToken").asText(), 401);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        JsonNode active = login("alice@example.com", "password123", 200);
        var user = users.findByEmail("alice@example.com").orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        refresh(active.get("refreshToken").asText(), 401);
    }

    @Test
    void refreshWorksWithoutAccessTokenEvenWhenPreviousAccessTokenExpired() throws Exception {
        String id = register("alice@example.com", "alice", "password123").get("id").asText();
        JsonNode auth = login("alice@example.com", "password123", 200);
        String expired = token(encoder, id, auth.get("sessionId").asText(), jwtProperties.getIssuer(), -120);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        refresh(auth.get("refreshToken").asText(), 200);
    }

    @Test
    void malformedAndUnknownRefreshTokensDoNotCreateSessions() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        refresh("malformed", 400);
        refresh("x".repeat(43), 401);
        assertEquals(0, sessions.count());
        assertEquals(0, refreshTokens.count());
    }

    @Test
    void refreshRollsBackRotationIfAccessTokenIssuanceFails() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode auth = login("alice@example.com", "password123", 200);
        doThrow(new IllegalStateException("simulated signing failure")).when(jwtService)
                .createAccessToken(anyString(), anyString(), any());
        refresh(auth.get("refreshToken").asText(), 500);
        assertEquals(1, refreshTokens.count());
        assertNull(refreshTokens.findAll().get(0).getConsumedAt());
        doCallRealMethod().when(jwtService).createAccessToken(anyString(), anyString(), any());
        refresh(auth.get("refreshToken").asText(), 200);
    }

    @Test
    void refreshedAccessTokenCannotOutliveSession() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode auth = login("alice@example.com", "password123", 200);
        var session = sessions.findById(auth.get("sessionId").asText()).orElseThrow();
        session.setExpiresAt(Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        sessions.saveAndFlush(session);
        JsonNode next = refresh(auth.get("refreshToken").asText(), 200);
        assertTrue(next.get("expiresIn").asLong() <= 120);
        assertTrue(next.get("expiresIn").asLong() > 0);
        assertTrue(next.get("refreshExpiresIn").asLong() <= 120);
    }

    @Test
    void simultaneousRefreshRequestsCannotBothUseSameToken() throws Exception {
        register("alice@example.com", "alice", "password123");
        JsonNode auth = login("alice@example.com", "password123", 200);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> task = () -> {
                start.await();
                return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(java.util.Map.of("refreshToken", auth.get("refreshToken").asText()))))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            var results = java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(results.contains(200));
            assertTrue(results.contains(401));
            assertEquals(2, refreshTokens.count());
            assertNotNull(sessions.findById(auth.get("sessionId").asText()).orElseThrow().getRevokedAt());
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode refresh(String rawToken, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("refreshToken", rawToken))))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) result.andExpect(header().string("Cache-Control", "no-store"));
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return expectedStatus == 200 ? body.get("data") : body;
    }

    @Test
    void profileUpdateReplacesFieldsAndCannotEditAnotherUser() throws Exception {
        String aliceId = register("alice@example.com", "alice", "password123").get("id").asText();
        String bobId = register("bob@example.com", "bob", "password123").get("id").asText();
        String token = login("alice@example.com", "password123", 200).get("accessToken").asText();
        mvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(aliceId));
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"Alice Updated","bio":"Hello","dateOfBirth":"2000-05-20","gender":"FEMALE"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.data.bio").value("Hello"))
                .andExpect(jsonPath("$.data.dateOfBirth").value("2000-05-20"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));
        mvc.perform(patch("/api/users/me/profile").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"Alice Updated","bio":null,"userId":"%s","avatarUrl":"https://untrusted.example/a.jpg"}
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
        for (String body : new String[]{"{", "[]", "null", "{}", "{\"fullName\":null}", "{\"fullName\":\"  \"}",
                "{\"fullName\":\"Alice\",\"dateOfBirth\":\"2999-01-01\"}", "{\"fullName\":\"Alice\",\"gender\":\"INVALID\"}",
                json.writeValueAsString(java.util.Map.of("fullName", "Alice", "bio", "x".repeat(1001)))}) {
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
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/verify-email'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/resend-verification'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").isArray())
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
        JsonNode registered = registerPending(email, username, password);
        verifyEmail(registered.get("email").asText(), lastOtp(), 200);
        return registered;
    }

    private JsonNode registerPending(String email, String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email, username, password)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration received; check your email for verification"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }

    private JsonNode login(String email, String password, int expectedStatus) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "TestBrowser Desktop")
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) {
            result.andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.refreshToken").isString())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        }
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return expectedStatus == 200 ? body.get("data") : body;
    }

    private String registration(String email, String username, String password) {
        return json.writeValueAsString(java.util.Map.of("email", email, "username", username,
                "password", password, "fullName", " Alice Nguyen "));
    }

    private String token(JwtEncoder signer, String subject, String sessionId, String issuer, long expiresIn) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(subject).issuer(issuer).issuedAt(now.minusSeconds(300))
                .expiresAt(now.plusSeconds(expiresIn));
        if (sessionId != null) claims.claim("sid", sessionId);
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build())).getTokenValue();
    }
}
