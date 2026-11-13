package com.findmypacks.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.user.AdminUserSeeder;
import com.findmypacks.user.AppUser;
import com.findmypacks.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    void sessionEndpointRestoresAuthenticatedUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/web/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"findmypacksadmin","password":"findmypacksadmin"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/web/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("findmypacksadmin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void sessionEndpointRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/web/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginLogsCorrelatedApiAndDatabaseEvents() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/web/login")
                            .with(csrf())
                            .header("X-Request-Id", "web-login-correlation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"findmypacksadmin","password":"findmypacksadmin"}
                                    """))
                    .andExpect(status().isOk());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode apiEvent = null;
            JsonNode dbEvent = null;
            for (ILoggingEvent loggedEvent : appender.list) {
                JsonNode event = mapper.readTree(loggedEvent.getFormattedMessage());
                if ("api".equals(event.path("layer").asText())
                        && "login".equals(event.path("operation").asText())) {
                    apiEvent = event;
                }
                if ("db".equals(event.path("layer").asText())) {
                    dbEvent = event;
                }
            }

            org.assertj.core.api.Assertions.assertThat(apiEvent).isNotNull();
            org.assertj.core.api.Assertions.assertThat(dbEvent).isNotNull();
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("request_id").asText())
                    .isEqualTo("web-login-correlation");
            org.assertj.core.api.Assertions.assertThat(dbEvent.path("request_id").asText())
                    .isEqualTo(apiEvent.path("request_id").asText());
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("client").asText()).isEqualTo("web");
            org.assertj.core.api.Assertions.assertThat(dbEvent.path("client").asText()).isEqualTo("web");
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("status").asText()).isEqualTo("success");
            org.assertj.core.api.Assertions.assertThat(dbEvent.path("status").asText()).isEqualTo("success");
            org.assertj.core.api.Assertions.assertThat(apiEvent.size()).isEqualTo(8);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void invalidCredentialsAreRejected() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/web/login")
                            .with(csrf())
                            .header("X-Request-Id", "web-login-failure")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"findmypacksadmin","password":"do-not-log-this-secret"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode apiEvent = null;
            JsonNode dbEvent = null;
            for (ILoggingEvent loggedEvent : appender.list) {
                String json = loggedEvent.getFormattedMessage();
                JsonNode event = mapper.readTree(json);
                if ("api".equals(event.path("layer").asText())
                        && "login".equals(event.path("operation").asText())) {
                    apiEvent = event;
                }
                if ("db".equals(event.path("layer").asText())) {
                    dbEvent = event;
                }
                org.assertj.core.api.Assertions.assertThat(json).doesNotContain("do-not-log-this-secret");
            }

            org.assertj.core.api.Assertions.assertThat(apiEvent).isNotNull();
            org.assertj.core.api.Assertions.assertThat(dbEvent).isNotNull();
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("status").asText()).isEqualTo("error");
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("level").asText()).isEqualTo("WARN");
            org.assertj.core.api.Assertions.assertThat(dbEvent.path("status").asText()).isEqualTo("success");
            org.assertj.core.api.Assertions.assertThat(apiEvent.path("request_id").asText())
                    .isEqualTo("web-login-failure");
            org.assertj.core.api.Assertions.assertThat(dbEvent.path("request_id").asText())
                    .isEqualTo("web-login-failure");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
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
