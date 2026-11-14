package com.chatsocial.Controller;

import com.chatsocial.dto.auth.LoginRequest;
import com.chatsocial.entity.*;
import com.chatsocial.enums.*;
import com.chatsocial.repository.*;
import com.chatsocial.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.chatsocial.service.BrevoEmailClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class AdminIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired AdminService adminService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean BrevoEmailClient mail;
    MockMvc mvc;
    User admin, member;
    String adminToken, memberToken, refreshToken;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        member = user(UserRole.USER, UserStatus.ACTIVE);
        adminToken = login(admin).getAccessToken();
        var session = login(member);
        memberToken = session.getAccessToken();
        refreshToken = session.getRefreshToken();
    }

    User user(UserRole role, UserStatus status) {
        String name = "admin_test_" + UUID.randomUUID().toString().replace("-", "");
        User user = new User(null, name + "@example.com", passwords.encode("Password123!"), name, status, null, null);
        user.setRole(role);
        user = users.saveAndFlush(user);
        profiles.saveAndFlush(new UserProfile(null, user, name, null, null, null, null, null));
        return user;
    }

    com.chatsocial.dto.auth.AuthResponse login(User user) {
        var request = new LoginRequest(); request.setEmail(user.getEmail()); request.setPassword("Password123!");
        return auth.login(request, "admin-tests");
    }

    @Test void requiresLiveAdminRoleAndNeverLeaksCredentials() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}/status", member.getId()).header("Authorization", "Bearer " + memberToken)
                .contentType("application/json").content("{\"suspended\":true,\"reason\":\"Violation\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param("q", member.getEmail().toUpperCase()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalUsers").value(users.countByRole(UserRole.USER)))
                .andExpect(jsonPath("$.data.totalMatches").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(member.getId()))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
        admin.setRole(UserRole.USER); users.saveAndFlush(admin);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
    }

    @Test void suspensionRevokesAccessAndRefreshAndUnlockRequiresNewLogin() throws Exception {
        change(member, true, "  Vi phạm quy định  ", 200);
        var locked = users.findById(member.getId()).orElseThrow();
        assertEquals(UserStatus.SUSPENDED, locked.getStatus());
        assertEquals("Vi phạm quy định", locked.getModerationReason());
        assertEquals(admin.getId(), locked.getModeratedBy());
        assertNotNull(locked.getModeratedAt());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + memberToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + member.getEmail() + "\",\"password\":\"Password123!\"}")).andExpect(status().isForbidden());
        change(member, false, "Đã xác minh lại", 200);
        assertEquals(UserStatus.ACTIVE, users.findById(member.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + memberToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + login(member).getAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test void validatesReasonsAndProtectsAdminsAndOtherAccountStates() throws Exception {
        change(member, true, "   ", 400);
        change(member, true, "x".repeat(501), 400);
        change(admin, true, "Không được tự khóa", 400);
        change(user(UserRole.ADMIN, UserStatus.ACTIVE), true, "Admin khác", 400);
        for (var status : new UserStatus[]{UserStatus.PENDING_VERIFICATION, UserStatus.DISABLED, UserStatus.DELETED}) {
            var user = user(UserRole.USER, status);
            change(user, false, "Không được bỏ qua trạng thái", 409);
            change(user, true, "Không được ghi đè trạng thái", 409);
        }
        change(member, false, "Chưa bị khóa", 409);
        change(member, true, "Khóa lần đầu", 200);
        change(member, true, "Yêu cầu đã cũ", 409);
    }

    @Test void listsNewestFirstAndTreatsSearchWildcardsLiterally() throws Exception {
        var newest = user(UserRole.USER, UserStatus.PENDING_VERIFICATION);
        var newestAdmin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(newest.getId()))
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.totalMatches").value(users.countByRole(UserRole.USER)))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param("q", newestAdmin.getEmail()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalMatches").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param("q", newestAdmin.getUsername()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        for (String[] invalid : new String[][]{{"page", "-1"}, {"size", "51"}, {"q", "x".repeat(256)}}) {
            mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void overviewRequiresAdminAndValidPeriod() throws Exception {
        mvc.perform(get("/api/admin/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + memberToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.registrations.length()").value(7))
                .andExpect(jsonPath("$.data.statuses.length()").value(5))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Ho_Chi_Minh"));
        mvc.perform(get("/api/admin/overview").param("days", "30").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.registrations.length()").value(30));
        mvc.perform(get("/api/admin/overview").param("days", "8").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test void overviewGroupsRegistrationsByVietnamDayAndKeepsZeroDays() {
        var before = adminService.overview(7);
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var midnight = today.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var yesterday = user(UserRole.USER, UserStatus.SUSPENDED);
        var outsidePeriod = user(UserRole.USER, UserStatus.DELETED);
        jdbc.update("update chatsocial.users set created_at = ? where user_id = ?",
                java.time.OffsetDateTime.ofInstant(midnight.minusSeconds(1), java.time.ZoneOffset.UTC), yesterday.getId());
        jdbc.update("update chatsocial.users set created_at = ? where user_id = ?",
                java.time.OffsetDateTime.ofInstant(midnight.minus(7, java.time.temporal.ChronoUnit.DAYS), java.time.ZoneOffset.UTC), outsidePeriod.getId());
        var after = adminService.overview(7);
        assertEquals(before.totalUsers() + 2, after.totalUsers());
        assertEquals(before.newUsersToday(), after.newUsersToday());
        assertEquals(before.newUsersInPeriod() + 1, after.newUsersInPeriod());
        assertEquals(today.minusDays(6), after.registrations().get(0).date());
        assertEquals(today, after.registrations().get(6).date());
        assertEquals(before.registrations().get(5).count() + 1, after.registrations().get(5).count());
        for (int day = 0; day < 5; day++) assertEquals(before.registrations().get(day).count(), after.registrations().get(day).count());
        assertEquals(after.totalUsers(), after.statuses().stream().mapToLong(AdminService.StatusTotal::count).sum());
        assertEquals(before.statuses().stream().filter(row -> row.status() == UserStatus.SUSPENDED).findFirst().orElseThrow().count() + 1,
                after.statuses().stream().filter(row -> row.status() == UserStatus.SUSPENDED).findFirst().orElseThrow().count());
    }

    void change(User user, boolean suspended, String reason, int expected) throws Exception {
        mvc.perform(patch("/api/admin/users/{id}/status", user.getId()).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"suspended\":" + suspended + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().is(expected));
    }
}
