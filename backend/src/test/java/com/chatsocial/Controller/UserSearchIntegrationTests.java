package com.chatsocial.Controller;

import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:usersearch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS chatsocial")
@ActiveProfiles("test")
class UserSearchIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @MockitoBean JavaMailSender mailSender;
    private MockMvc mvc;
    private User viewer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        profiles.deleteAll();
        users.deleteAll();
        viewer = user("viewer", UserStatus.ACTIVE);
    }

    @Test
    void usernameSearchIgnoresCaseAndReturnsOnlyPublicSummaryOfOtherActiveUsers() throws Exception {
        User match = user("minh_anh", UserStatus.ACTIVE);
        profiles.saveAndFlush(new UserProfile(null, match, "Minh Anh", "/avatar.png", null,
                "private bio", null, null));
        user("minh_disabled", UserStatus.DISABLED);
        user("minh_deleted", UserStatus.DELETED);
        user("minh_pending", UserStatus.PENDING_VERIFICATION);
        mvc.perform(get("/api/users/search").param("q", " @MINH ")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(match.getId()))
                .andExpect(jsonPath("$.data.items[0].fullName").value("Minh Anh"))
                .andExpect(jsonPath("$.data.items[0].avatarUrl").value("/avatar.png"))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].bio").doesNotExist());
        mvc.perform(get("/api/users/search").param("q", "viewer")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void emailRequiresExactMatchAndNormalizesCaseAndWhitespace() throws Exception {
        User match = user("alice", UserStatus.ACTIVE);
        mvc.perform(get("/api/users/search").param("q", " ALICE@EXAMPLE.COM ")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(match.getId()));
        mvc.perform(get("/api/users/search").param("q", "alice@")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void likeWildcardsAreLiteralAndPaginationIsStable() throws Exception {
        user("alpha_beta", UserStatus.ACTIVE);
        user("alphaxbeta", UserStatus.ACTIVE);
        mvc.perform(get("/api/users/search").param("q", "_beta")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("alpha_beta"));
        mvc.perform(get("/api/users/search").param("q", "%%")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/users/search").param("q", "alpha").param("size", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].username").value("alpha_beta"))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mvc.perform(get("/api/users/search").param("q", "alpha").param("size", "1").param("page", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].username").value("alphaxbeta"))
                .andExpect(jsonPath("$.data.items[0].fullName").value("alphaxbeta"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void searchRequiresAuthenticationAndRejectsInvalidInputs() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "alice")).andExpect(status().isUnauthorized());
        for (String value : new String[]{"", " ", "a", "@a", "a".repeat(256)}) {
            mvc.perform(get("/api/users/search").param("q", value)
                            .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/users/search").param("q", "alice").param("page", "-1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/search").param("q", "alice").param("size", "51")
                        .with(jwt().jwt(token -> token.subject(viewer.getId()))))
                .andExpect(status().isBadRequest());
    }

    private User user(String username, UserStatus status) {
        return users.saveAndFlush(new User(null, username + "@example.com", "hash", username, status, null, null));
    }
}
