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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CliPackageExportApiIntegrationTest {
    private static final String URL = "/api/cli/records/export";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository records;

    @BeforeEach
    void cleanTable() {
        records.deleteAll();
    }

    @Test
    void requiresBearerAndExportsEveryFilteredRecordWithoutPaging() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (int index = 1; index <= 7; index++) {
            records.save(new PackageRecord("CODE%05d".formatted(index), "Maria %d".formatted(index),
                    "Fortaleza", "CORREIOS", "POSTED", LocalDate.of(2026, 1, index)));
        }
        records.save(new PackageRecord("OTHER001", "Ana", "Recife", "JADLOG", "DELIVERED",
                LocalDate.of(2026, 2, 1)));

        String csv = mockMvc.perform(get(URL).param("customer", "maria").param("destination", "fort")
                        .param("carrier", "correios").param("status", "posted")
                        .param("trackingCode", "code").param("sort", "trackingCode,asc")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"pacotes.csv\""))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.split("\\r\\n")).hasSize(8);
        assertThat(csv).contains("CODE00001,Maria 1", "CODE00007,Maria 7").doesNotContain("OTHER001");
    }

    @Test
    void exportsAllWithoutFiltersAndEscapesCsvCells() throws Exception {
        records.save(new PackageRecord("+FORMULA", "Ana, \"Nina\"\nSilva", "=1+1",
                "CORREIOS", "POSTED", LocalDate.of(2026, 1, 1)));

        String csv = mockMvc.perform(get(URL).header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("'+FORMULA,\"Ana, \"\"Nina\"\"\nSilva\",'=1+1");
    }

    @Test
    void validatesFiltersAndLogsCorrelatedApiAndDatabaseEvents() throws Exception {
        String bearer = "Bearer " + token();
        mockMvc.perform(get(URL).param("carrier", "unknown").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("sort", "unknown,asc").header("Authorization", bearer))
                .andExpect(status().isBadRequest());

        Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get(URL).header("Authorization", bearer)
                            .header("X-Request-Id", "cli-export-correlation"))
                    .andExpect(status().isOk());
            List<JsonNode> events = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            for (ILoggingEvent event : appender.list) events.add(mapper.readTree(event.getFormattedMessage()));
            List<JsonNode> exportEvents = events.stream()
                    .filter(event -> "export_packages".equals(event.path("operation").asText())).toList();
            assertThat(exportEvents).extracting(event -> event.path("layer").asText())
                    .contains("api", "db");
            assertThat(exportEvents).allSatisfy(event -> {
                assertThat(event.path("request_id").asText()).isEqualTo("cli-export-correlation");
                assertThat(event.path("client").asText()).isEqualTo("cli");
            });
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
}
