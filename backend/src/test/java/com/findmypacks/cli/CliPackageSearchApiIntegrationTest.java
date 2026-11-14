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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CliPackageSearchApiIntegrationTest {
    private static final String CLI_URL = "/api/cli/records/search";
    private static final String WEB_URL = "/api/web/records/search";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository records;

    @BeforeEach
    void seedPackages() {
        records.deleteAll();
        records.save(new PackageRecord("ABCD00001", "Maria Silva", "Fortaleza",
                "CORREIOS", "POSTED", LocalDate.now().minusDays(2)));
        records.save(new PackageRecord("EFGH00002", "Ana Lima", "Recife",
                "JADLOG", "IN_TRANSIT", LocalDate.now().minusDays(1)));
        records.save(new PackageRecord("IJKL00003", "Maria Souza", "Fortaleza",
                "LOGGI", "DELIVERED", LocalDate.now()));
        records.save(new PackageRecord("MNOP00004", "João Costa", "Natal",
                "OTHER", "POSTED", LocalDate.now()));
    }

    @Test
    void cliSearchRequiresBearerAndSupportsThreeCombinedFilters() throws Exception {
        mockMvc.perform(get(CLI_URL).param("customer", "maria"))
                .andExpect(status().isUnauthorized());

        MvcResult result = mockMvc.perform(get(CLI_URL)
                        .param("customer", "MARIA")
                        .param("destination", "FORTALEZA")
                        .param("status", "delivered")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("content");
        assertThat(content.findValuesAsText("trackingCode")).containsExactly("IJKL00003");
    }

    @Test
    void cliSearchMatchesWebSearchResultsAndPaging() throws Exception {
        String bearer = "Bearer " + token();
        var cliResult = mockMvc.perform(get(CLI_URL)
                        .param("customer", "maria")
                        .param("destination", "fortaleza")
                        .param("page", "0").param("size", "1").param("sort", "trackingCode,asc")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        var webResult = mockMvc.perform(get(WEB_URL)
                        .param("customer", "maria")
                        .param("destination", "fortaleza")
                        .param("page", "0").param("size", "1").param("sort", "trackingCode,asc")
                        .with(admin()))
                .andExpect(status().isOk()).andReturn();

        JsonNode cliPage = new ObjectMapper().readTree(cliResult.getResponse().getContentAsString());
        JsonNode webPage = new ObjectMapper().readTree(webResult.getResponse().getContentAsString());
        assertThat(cliPage.path("content").findValuesAsText("trackingCode"))
                .containsExactlyElementsOf(webPage.path("content").findValuesAsText("trackingCode"));
        assertThat(cliPage.path("totalElements").asLong()).isEqualTo(webPage.path("totalElements").asLong());
        assertThat(cliPage.path("totalPages").asInt()).isEqualTo(webPage.path("totalPages").asInt());
        assertThat(cliPage.path("content").findValuesAsText("trackingCode"))
                .containsExactly("ABCD00001");
    }

    @Test
    void cliAndWebSearchAcceptAllFiveFiltersTogether() throws Exception {
        String bearer = "Bearer " + token();
        var cliResult = mockMvc.perform(get(CLI_URL)
                        .param("trackingCode", "abcd0000")
                        .param("customer", "maria")
                        .param("destination", "fort")
                        .param("carrier", "correios")
                        .param("status", "posted")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        var webResult = mockMvc.perform(get(WEB_URL)
                        .param("trackingCode", "abcd0000")
                        .param("customer", "maria")
                        .param("destination", "fort")
                        .param("carrier", "correios")
                        .param("status", "posted")
                        .with(admin()))
                .andExpect(status().isOk()).andReturn();

        JsonNode cliPage = new ObjectMapper().readTree(cliResult.getResponse().getContentAsString());
        JsonNode webPage = new ObjectMapper().readTree(webResult.getResponse().getContentAsString());
        assertThat(cliPage.path("content").findValuesAsText("trackingCode"))
                .containsExactlyElementsOf(webPage.path("content").findValuesAsText("trackingCode"))
                .containsExactly("ABCD00001");
    }

    @Test
    void cliSearchValidatesFiltersPagingAndSortingLikeWebSearch() throws Exception {
        String bearer = "Bearer " + token();
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("carrier", "unknown"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("status", "lost"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("sort", "unknown,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("sort", "customer,sideways"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("sort", "customer,asc,extra"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("customer", "  "))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.totalElements").value(4));
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("carrier", ""))
                .andExpect(status().isOk());
        mockMvc.perform(get(CLI_URL).header("Authorization", bearer).param("status", " "))
                .andExpect(status().isOk());
    }

    @Test
    void logsCorrelatedCliApiAndDatabaseSearchEvents() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get(CLI_URL).param("status", "POSTED")
                            .header("Authorization", "Bearer " + token())
                            .header("X-Request-Id", "cli-search-correlation"))
                    .andExpect(status().isOk());
            ObjectMapper mapper = new ObjectMapper();
            List<JsonNode> events = new ArrayList<>();
            for (ILoggingEvent event : appender.list) events.add(mapper.readTree(event.getFormattedMessage()));
            JsonNode api = events.stream().filter(e -> "api".equals(e.path("layer").asText())
                    && "search_packages".equals(e.path("operation").asText())).findFirst().orElseThrow();
            JsonNode db = events.stream().filter(e -> "db".equals(e.path("layer").asText())
                    && "search_packages".equals(e.path("operation").asText())).findFirst().orElseThrow();
            assertThat(api.path("request_id").asText()).isEqualTo("cli-search-correlation");
            assertThat(db.path("request_id").asText()).isEqualTo("cli-search-correlation");
            assertThat(api.path("client").asText()).isEqualTo("cli");
            assertThat(db.path("client").asText()).isEqualTo("cli");
            assertThat(api.path("status").asText()).isEqualTo("success");
            assertThat(db.path("status").asText()).isEqualTo("success");
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
