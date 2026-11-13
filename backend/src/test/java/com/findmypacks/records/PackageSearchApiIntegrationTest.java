package com.findmypacks.records;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.logging.StructuredOperationLogger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageSearchApiIntegrationTest {
    private static final String URL = "/api/web/records/search";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository records;

    @BeforeEach
    void cleanTable() {
        records.deleteAll();
        records.save(new PackageRecord("ABCD00001", "Maria Silva", "Fortaleza", "CORREIOS", "POSTED", LocalDate.now().minusDays(2)));
        records.save(new PackageRecord("EFGH00002", "Ana Lima", "Recife", "JADLOG", "IN_TRANSIT", LocalDate.now().minusDays(1)));
        records.save(new PackageRecord("IJKL00003", "Maria Souza", "Fortaleza", "LOGGI", "DELIVERED", LocalDate.now()));
        records.save(new PackageRecord("MNOP00004", "João Costa", "Natal", "OTHER", "POSTED", LocalDate.now()));
    }

    private static RequestPostProcessor admin() { return user("findmypacksadmin").roles("ADMIN"); }

    @Test
    void filtersIndividuallyByTrackingCodeCustomerDestinationCarrierAndStatus() throws Exception {
        expectSingle("trackingCode", "abcd00001", "ABCD00001");
        expectSingle("customer", "maria", "ABCD00001", "IJKL00003");
        expectSingle("destination", "fortaleza", "ABCD00001", "IJKL00003");
        expectSingle("carrier", "correios", "ABCD00001");
        expectSingle("status", "in_transit", "EFGH00002");
    }

    @Test
    void combinesPairsOfFilters() throws Exception {
        expectSingle("customer", "maria", "status", "POSTED", "ABCD00001");
        expectSingle("destination", "fortaleza", "carrier", "LOGGI", "IJKL00003");
    }

    @Test
    void combinesThreeFiltersAndIgnoresTextCase() throws Exception {
        expectSingle("customer", "MARIA", "destination", "FORTALEZA", "status", "delivered", "IJKL00003");
    }

    @Test
    void emptySearchReturnsNoResults() throws Exception {
        mockMvc.perform(get(URL).param("customer", "not a customer").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void blankFiltersAreIgnored() throws Exception {
        mockMvc.perform(get(URL).param("customer", "   ").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void filtersBeforePaginationAndSorting() throws Exception {
        mockMvc.perform(get(URL).param("status", "POSTED").param("page", "0").param("size", "1")
                        .param("sort", "customer,asc").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].customer").value("João Costa"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void acceptsSortFieldWithoutDirectionAsAscending() throws Exception {
        mockMvc.perform(get(URL).param("sort", "customer").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].customer").value("Ana Lima"));
    }

    @Test
    void invalidFilterValuesReturn400() throws Exception {
        mockMvc.perform(get(URL).param("carrier", "UNKNOWN").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("status", "LOST").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("page", "invalid").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "0").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "unknown,asc").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "customer,sideways").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void searchWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void logsCorrelatedApiAndDatabaseSearchEvents() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get(URL).param("status", "POSTED").with(admin())
                            .header("X-Request-Id", "search-correlation"))
                    .andExpect(status().isOk());
            ObjectMapper mapper = new ObjectMapper();
            List<JsonNode> events = new ArrayList<>();
            for (ILoggingEvent event : appender.list) events.add(mapper.readTree(event.getFormattedMessage()));
            JsonNode api = events.stream().filter(e -> "api".equals(e.path("layer").asText())).findFirst().orElseThrow();
            JsonNode db = events.stream().filter(e -> "db".equals(e.path("layer").asText())).findFirst().orElseThrow();
            assertThat(api.path("operation").asText()).isEqualTo("search_packages");
            assertThat(db.path("operation").asText()).isEqualTo("search_packages");
            assertThat(api.path("request_id").asText()).isEqualTo("search-correlation");
            assertThat(db.path("request_id").asText()).isEqualTo("search-correlation");
            assertThat(api.path("client").asText()).isEqualTo("web");
            assertThat(db.path("client").asText()).isEqualTo("web");
            assertThat(api.path("status").asText()).isEqualTo("success");
            assertThat(db.path("status").asText()).isEqualTo("success");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void expectSingle(String name, String value, String... codes) throws Exception {
        var result = mockMvc.perform(get(URL).param(name, value).with(admin())).andExpect(status().isOk())
                .andReturn();
        JsonNode content = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("content");
        assertThat(content.findValuesAsText("trackingCode")).containsExactlyInAnyOrder(codes);
    }

    private void expectSingle(String name1, String value1, String name2, String value2, String expected) throws Exception {
        var result = mockMvc.perform(get(URL).param(name1, value1).param(name2, value2).with(admin()))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("content");
        assertThat(content.findValuesAsText("trackingCode")).containsExactly(expected);
    }

    private void expectSingle(String name1, String value1, String name2, String value2,
                              String name3, String value3, String expected) throws Exception {
        var result = mockMvc.perform(get(URL).param(name1, value1).param(name2, value2).param(name3, value3).with(admin()))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = new ObjectMapper().readTree(result.getResponse().getContentAsString()).path("content");
        assertThat(content.findValuesAsText("trackingCode")).containsExactly(expected);
    }
}
