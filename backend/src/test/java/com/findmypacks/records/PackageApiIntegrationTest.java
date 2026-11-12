package com.findmypacks.records;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.logging.StructuredOperationLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageApiIntegrationTest {
    private static final String URL = "/api/web/records";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PackageRecordRepository packages;

    @BeforeEach
    void cleanTable() {
        packages.deleteAll();
    }

    private static RequestPostProcessor admin() {
        return user("findmypacksadmin").roles("ADMIN");
    }

    private static String payload(String trackingCode, String postedAt) {
        return """
                {"trackingCode":"%s","customer":"Maria Silva","destination":"Fortaleza",
                 "carrier":"CORREIOS","status":"POSTED","postedAt":"%s"}
                """.formatted(trackingCode, postedAt);
    }

    private static String payload(String trackingCode) {
        return payload(trackingCode, LocalDate.now().minusDays(1).toString());
    }

    private PackageRecord savedPackage(String trackingCode) {
        return packages.save(new PackageRecord(
                trackingCode, "Maria Silva", "Fortaleza", "CORREIOS", "POSTED",
                LocalDate.now().minusDays(1)));
    }

    // ---------- POST /api/web/records ----------

    @Test
    void createPersistsPackageAndReturns201() throws Exception {
        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.trackingCode").value("ABC12345"))
                .andExpect(jsonPath("$.customer").value("Maria Silva"))
                .andExpect(jsonPath("$.destination").value("Fortaleza"))
                .andExpect(jsonPath("$.carrier").value("CORREIOS"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.postedAt").value(LocalDate.now().minusDays(1).toString()));

        assertThat(packages.findAll()).hasSize(1);
        assertThat(packages.existsByTrackingCode("ABC12345")).isTrue();
    }

    @Test
    void createWithoutAuthenticationReturns401AndSavesNothing() throws Exception {
        mockMvc.perform(post(URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345")))
                .andExpect(status().isUnauthorized());

        assertThat(packages.count()).isZero();
    }

    @Test
    void createWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trackingCode":"abc","customer":"","destination":"F",
                                 "carrier":"UNKNOWN","status":"LOST","postedAt":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.trackingCode").exists())
                .andExpect(jsonPath("$.fieldErrors.customer").exists())
                .andExpect(jsonPath("$.fieldErrors.destination").exists())
                .andExpect(jsonPath("$.fieldErrors.carrier").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists())
                .andExpect(jsonPath("$.fieldErrors.postedAt").exists());

        assertThat(packages.count()).isZero();
    }

    @Test
    void createWithFuturePostedDateReturns400() throws Exception {
        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345", LocalDate.now().plusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.postedAt").value("Posted date cannot be in the future"));
    }

    @Test
    void createWithMalformedBodyReturns400() throws Exception {
        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345", "2026-13-45")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void createWithDuplicateTrackingCodeReturns409() throws Exception {
        savedPackage("ABC12345");

        mockMvc.perform(post(URL).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tracking code already registered"));

        assertThat(packages.count()).isEqualTo(1);
    }

    // ---------- PUT /api/web/records/{id} ----------

    @Test
    void updateChangesPersistedPackage() throws Exception {
        PackageRecord existing = savedPackage("ABC12345");

        mockMvc.perform(put(URL + "/" + existing.getId()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NEW12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.trackingCode").value("NEW12345"));

        PackageRecord reloaded = packages.findById(existing.getId()).orElseThrow();
        assertThat(reloaded.getTrackingCode()).isEqualTo("NEW12345");
        assertThat(packages.count()).isEqualTo(1);
    }

    @Test
    void updateKeepingOwnTrackingCodeIsAllowed() throws Exception {
        PackageRecord existing = savedPackage("ABC12345");

        mockMvc.perform(put(URL + "/" + existing.getId()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345")))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithTrackingCodeOfAnotherPackageReturns409() throws Exception {
        PackageRecord first = savedPackage("ABC12345");
        savedPackage("XYZ98765");

        mockMvc.perform(put(URL + "/" + first.getId()).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("XYZ98765")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tracking code already registered"));

        assertThat(packages.findById(first.getId()).orElseThrow().getTrackingCode()).isEqualTo("ABC12345");
    }

    @Test
    void updateUnknownPackageReturns404() throws Exception {
        mockMvc.perform(put(URL + "/999999").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ABC12345")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Package not found"));
    }

    @Test
    void updateWithoutAuthenticationReturns401() throws Exception {
        PackageRecord existing = savedPackage("ABC12345");

        mockMvc.perform(put(URL + "/" + existing.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("NEW12345")))
                .andExpect(status().isUnauthorized());

        assertThat(packages.findById(existing.getId()).orElseThrow().getTrackingCode()).isEqualTo("ABC12345");
    }

    // ---------- Tratamento de erros ----------

    @Test
    void integrityViolationIsMappedToConflict() {
        var response = new PackageExceptionHandler().integrity();

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Tracking code already registered");
    }

    // ---------- Logs correlacionados ----------

    @Test
    void createLogsCorrelatedApiAndDatabaseEvents() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(post(URL).with(admin()).with(csrf())
                            .header("X-Request-Id", "package-create-correlation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload("ABC12345")))
                    .andExpect(status().isCreated());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode apiEvent = null;
            List<JsonNode> dbEvents = new ArrayList<>();
            for (ILoggingEvent loggedEvent : appender.list) {
                JsonNode event = mapper.readTree(loggedEvent.getFormattedMessage());
                if ("api".equals(event.path("layer").asText())) {
                    apiEvent = event;
                } else if ("db".equals(event.path("layer").asText())) {
                    dbEvents.add(event);
                }
            }

            assertThat(apiEvent).isNotNull();
            assertThat(apiEvent.path("operation").asText()).isEqualTo("create");
            assertThat(apiEvent.path("client").asText()).isEqualTo("web");
            assertThat(apiEvent.path("status").asText()).isEqualTo("success");
            assertThat(apiEvent.path("request_id").asText()).isEqualTo("package-create-correlation");
            assertThat(apiEvent.size()).isEqualTo(8);

            assertThat(dbEvents).isNotEmpty();
            assertThat(dbEvents).extracting(event -> event.path("operation").asText()).contains("insert");
            assertThat(dbEvents).allSatisfy(event -> {
                assertThat(event.path("request_id").asText()).isEqualTo("package-create-correlation");
                assertThat(event.path("client").asText()).isEqualTo("web");
                assertThat(event.path("status").asText()).isEqualTo("success");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}