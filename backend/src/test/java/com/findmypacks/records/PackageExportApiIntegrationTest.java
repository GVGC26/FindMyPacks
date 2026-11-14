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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageExportApiIntegrationTest {
    private static final String URL = "/api/web/records/export";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository records;

    @BeforeEach
    void cleanTable() {
        records.deleteAll();
    }

    private static RequestPostProcessor admin() { return user("findmypacksadmin").roles("ADMIN"); }

    private PackageRecord save(String code, String customer, String destination, String carrier, String status) {
        return records.save(new PackageRecord(code, customer, destination, carrier, status, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void exportsAllRecordsBeyondFirstPageAndReturnsDownloadHeaders() throws Exception {
        for (int index = 1; index <= 7; index++) {
            save("CODE%05d".formatted(index), "Cliente %d".formatted(index), "Fortaleza", "CORREIOS", "POSTED");
        }

        var result = mockMvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pacotes.csv\""))
                .andReturn();

        String csv = result.getResponse().getContentAsString();
        assertThat(csv.split("\\r\\n")).hasSize(8);
        assertThat(csv).contains("CODE00007,Cliente 7,Fortaleza,CORREIOS,POSTED,2026-01-01");
    }

    @Test
    void filtersByEachFieldAndByCombinations() throws Exception {
        save("ABCD00001", "Maria Silva", "Fortaleza", "CORREIOS", "POSTED");
        save("EFGH00002", "Ana Lima", "Recife", "JADLOG", "IN_TRANSIT");
        save("IJKL00003", "Maria Souza", "Fortaleza", "LOGGI", "DELIVERED");

        assertCsvContains("trackingCode", "abcd", "ABCD00001");
        assertCsvContains("customer", "maria", "ABCD00001", "IJKL00003");
        assertCsvContains("destination", "recife", "EFGH00002");
        assertCsvContains("carrier", "jadlog", "EFGH00002");
        assertCsvContains("status", "delivered", "IJKL00003");
        var combined = mockMvc.perform(get(URL).param("customer", "maria").param("destination", "fortaleza")
                        .param("status", "POSTED").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dataRows(combined)).containsExactly("ABCD00001,Maria Silva,Fortaleza,CORREIOS,POSTED,2026-01-01");
    }

    @Test
    void returnsOnlyHeaderWhenThereAreNoMatches() throws Exception {
        save("ABCD00001", "Maria", "Fortaleza", "CORREIOS", "POSTED");
        mockMvc.perform(get(URL).param("customer", "missing").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string("trackingCode,customer,destination,carrier,status,postedAt"));
    }

    @Test
    void escapesCsvFieldsAndPrefixesFormulaCells() throws Exception {
        save("+FORMULA", "Ana, \"Nina\"\nSilva", "=1+1", "CORREIOS", "POSTED");

        var response = mockMvc.perform(get(URL).with(admin())).andExpect(status().isOk()).andReturn();

        assertThat(response.getResponse().getContentAsString())
                .contains("'+FORMULA,\"Ana, \"\"Nina\"\"\nSilva\",'=1+1,CORREIOS,POSTED,2026-01-01");
    }

    @Test
    void sortsExportedRecordsAsRequested() throws Exception {
        save("CODE00001", "Zelda", "Fortaleza", "CORREIOS", "POSTED");
        save("CODE00002", "Ana", "Recife", "JADLOG", "IN_TRANSIT");

        var response = mockMvc.perform(get(URL).param("sort", "customer,asc").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(dataRows(response)).extracting(row -> row.split(",")[1]).containsExactly("Ana", "Zelda");
    }

    @Test
    void rejectsInvalidCarrierStatusAndSort() throws Exception {
        mockMvc.perform(get(URL).param("carrier", "UNKNOWN").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("status", "LOST").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "unknown,asc").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "customer,sideways").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void exportWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void logsCorrelatedApiAndDatabaseExportEvents() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get(URL).with(admin()).header("X-Request-Id", "export-correlation"))
                    .andExpect(status().isOk());
            ObjectMapper mapper = new ObjectMapper();
            List<JsonNode> events = new ArrayList<>();
            for (ILoggingEvent event : appender.list) events.add(mapper.readTree(event.getFormattedMessage()));
            JsonNode api = events.stream().filter(e -> "api".equals(e.path("layer").asText())).findFirst().orElseThrow();
            JsonNode db = events.stream().filter(e -> "db".equals(e.path("layer").asText())).findFirst().orElseThrow();
            assertThat(api.path("operation").asText()).isEqualTo("export_packages");
            assertThat(db.path("operation").asText()).isEqualTo("export_packages");
            assertThat(api.path("request_id").asText()).isEqualTo("export-correlation");
            assertThat(db.path("request_id").asText()).isEqualTo("export-correlation");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void acceptsSortFieldWithoutDirectionAsAscending() throws Exception {
        save("CODE00001", "Zelda", "Fortaleza", "CORREIOS", "POSTED");
        save("CODE00002", "Ana", "Recife", "JADLOG", "IN_TRANSIT");

        var response = mockMvc.perform(get(URL).param("sort", "customer").with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(dataRows(response).get(0)).contains(",Ana,");
    }

    private void assertCsvContains(String filter, String value, String... expectedCodes) throws Exception {
        var response = mockMvc.perform(get(URL).param(filter, value).with(admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(dataRows(response)).extracting(row -> row.split(",", 2)[0]).containsExactlyInAnyOrder(expectedCodes);
    }

    private List<String> dataRows(String csv) {
        String[] lines = csv.split("\\r\\n");
        return lines.length <= 1 ? List.of() : List.of(lines).subList(1, lines.length);
    }
}
