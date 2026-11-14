package com.findmypacks.cli;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.records.PackageRecord;
import com.findmypacks.records.PackageRecordRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CliDashboardApiIntegrationTest {
    private static final String CLI_URL = "/api/cli/dashboard";
    private static final String WEB_URL = "/api/web/dashboard";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository records;

    @BeforeEach
    void seedPackages() {
        records.deleteAll();
        LocalDate today = LocalDate.now();
        records.save(new PackageRecord("ABCD00001", "Maria Silva", "Fortaleza",
                "CORREIOS", "POSTED", today));
        records.save(new PackageRecord("EFGH00002", "Ana Lima", "Recife",
                "JADLOG", "IN_TRANSIT", today.minusDays(1)));
        records.save(new PackageRecord("IJKL00003", "Maria Souza", "Natal",
                "LOGGI", "DELIVERED", today.minusDays(7)));
    }

    @Test
    void cliDashboardRequiresBearerAuthentication() throws Exception {
        mockMvc.perform(get(CLI_URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cliDashboardReturnsTheWebIndicatorsAndCorrelatedLogs() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            String bearer = "Bearer " + token();
            var cliResult = mockMvc.perform(get(CLI_URL)
                            .header("Authorization", bearer)
                            .header("X-Request-Id", "cli-dashboard-correlation"))
                    .andExpect(status().isOk())
                    .andReturn();
            var webResult = mockMvc.perform(get(WEB_URL).with(admin()))
                    .andExpect(status().isOk())
                    .andReturn();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode cliSummary = mapper.readTree(cliResult.getResponse().getContentAsString());
            JsonNode webSummary = mapper.readTree(webResult.getResponse().getContentAsString());
            assertThat(cliSummary).isEqualTo(webSummary);
            assertThat(cliSummary.path("byStatus").findValuesAsText("label"))
                    .containsExactly("DELIVERED", "IN_TRANSIT", "POSTED");
            assertThat(cliSummary.path("byStatus").findValuesAsText("count"))
                    .containsExactly("1", "1", "1");
            assertThat(cliSummary.path("byCarrier").findValuesAsText("label"))
                    .containsExactly("CORREIOS", "JADLOG", "LOGGI");
            assertThat(cliSummary.path("postedPerDay")).hasSize(7);
            assertThat(cliSummary.path("postedPerDay").get(0).path("count").asLong()).isZero();
            assertThat(cliSummary.path("postedPerDay").get(6).path("count").asLong()).isEqualTo(1);

            List<JsonNode> events = new ArrayList<>();
            for (ILoggingEvent event : appender.list) {
                events.add(mapper.readTree(event.getFormattedMessage()));
            }
            JsonNode apiEvent = events.stream()
                    .filter(event -> "api".equals(event.path("layer").asText())
                            && "dashboard".equals(event.path("operation").asText())
                            && "cli".equals(event.path("client").asText()))
                    .findFirst().orElseThrow();
            JsonNode dbEvent = events.stream()
                    .filter(event -> "db".equals(event.path("layer").asText())
                            && "dashboard_summary".equals(event.path("operation").asText())
                            && "cli".equals(event.path("client").asText()))
                    .findFirst().orElseThrow();
            assertThat(apiEvent.path("request_id").asText()).isEqualTo("cli-dashboard-correlation");
            assertThat(dbEvent.path("request_id").asText()).isEqualTo("cli-dashboard-correlation");
            assertThat(apiEvent.path("status").asText()).isEqualTo("success");
            assertThat(dbEvent.path("status").asText()).isEqualTo("success");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private String token() throws Exception {
        String response = mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"findmypacksadmin\",\"password\":\"findmypacksadmin\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).path("token").asText();
    }

    private static RequestPostProcessor admin() {
        return user("findmypacksadmin").roles("ADMIN");
    }
}
