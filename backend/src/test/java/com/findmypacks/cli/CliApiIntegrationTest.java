package com.findmypacks.cli;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.findmypacks.user.AdminUserSeeder;
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
class CliApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserSeeder adminUserSeeder;
    @Autowired private AppUserRepository users;

    @BeforeEach
    void seedAdmin() {
        adminUserSeeder.run(new DefaultApplicationArguments());
    }

    @Test
    void healthIsPublicAndUsesCliRequestCorrelation() throws Exception {
        mockMvc.perform(get("/api/cli/health").header("X-Request-Id", "cli-health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "cli-health"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tokenRejectsInvalidCredentialsAndReturnsBearerForValidCredentials() throws Exception {
        mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"findmypacksadmin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"findmypacksadmin\",\"password\":\"findmypacksadmin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    @Test
    void tokenValidatesRequiredCredentials() throws Exception {
        mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedStatusRejectsMissingInvalidAndMalformedTokens() throws Exception {
        mockMvc.perform(get("/api/cli/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cli/status").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cli/status").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedStatusUsesBearerTokenAndAccessesDatabase() throws Exception {
        String token = token();
        mockMvc.perform(get("/api/cli/status").header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "cli-status-correlation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("findmypacksadmin"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        users.deleteAll();
        mockMvc.perform(get("/api/cli/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MISSING"));
    }

    private String token() throws Exception {
        String response = mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"findmypacksadmin\",\"password\":\"findmypacksadmin\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token", not("")))
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("token").asText();
    }
}
