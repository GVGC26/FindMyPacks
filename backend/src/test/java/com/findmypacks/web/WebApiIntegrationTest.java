package com.findmypacks.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.findmypacks.user.AdminUserSeeder;
import com.findmypacks.user.AppUser;
import com.findmypacks.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WebApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AdminUserSeeder adminUserSeeder;

    @BeforeEach
    void seedAdmin() {
        adminUserSeeder.run(new DefaultApplicationArguments());
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/web/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void validRequestIdIsReturnedForCorrelation() throws Exception {
        mockMvc.perform(get("/api/web/health").header("X-Request-Id", "test-request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-123"));
    }

    @Test
    void invalidRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/web/health").header("X-Request-Id", "invalid request id"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("invalid request id")));
    }

    @Test
    void csrfEndpointReturnsToken() throws Exception {
        mockMvc.perform(get("/api/web/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void initialAdminIsStoredWithEncodedPassword() {
        AppUser admin = users.findByUsername("findmypacksadmin").orElseThrow();

        org.assertj.core.api.Assertions.assertThat(admin.getId()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(admin.getRole()).isEqualTo("ADMIN");
        org.assertj.core.api.Assertions.assertThat(admin.getPasswordHash()).startsWith("$2");
    }

    @Test
    void protectedPathsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/web/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededAdminCanLogInAndReceivesSessionCookie() throws Exception {
        mockMvc.perform(post("/api/web/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"findmypacksadmin","password":"findmypacksadmin"}
                                """))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttribute("SPRING_SECURITY_CONTEXT", notNullValue()))
                .andExpect(jsonPath("$.username").value("findmypacksadmin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        mockMvc.perform(post("/api/web/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"findmypacksadmin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void unknownUsersAreRejected() throws Exception {
        mockMvc.perform(post("/api/web/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"unknown","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
