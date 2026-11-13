package com.findmypacks.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.records.PackageRecord;
import com.findmypacks.records.PackageRecordRepository;
import com.findmypacks.user.AdminUserSeeder;
import java.time.LocalDate;
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
class CliPackageApiIntegrationTest {
    private static final String URL = "/api/cli/records";

    @Autowired private MockMvc mockMvc;
    @Autowired private PackageRecordRepository packages;
    @Autowired private AdminUserSeeder adminUserSeeder;

    @BeforeEach
    void prepareDatabase() {
        packages.deleteAll();
        adminUserSeeder.run(new DefaultApplicationArguments());
    }

    @Test
    void createRequiresBearerValidatesAndPersistsPackage() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(payload("CLI12345")))
                .andExpect(status().isUnauthorized());

        String token = token();
        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("bad")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.trackingCode").exists());

        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "cli-create-correlation")
                        .contentType(MediaType.APPLICATION_JSON).content(payload("CLI12345")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").value("CLI12345"));
        assertThat(packages.existsByTrackingCode("CLI12345")).isTrue();

        mockMvc.perform(post(URL).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("CLI12345")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tracking code already registered"));
    }

    @Test
    void updatePersistsChangesAndHandlesMissingAndDuplicatePackages() throws Exception {
        PackageRecord first = packages.save(record("CLI12345"));
        packages.save(record("OTHER123"));
        String token = token();

        mockMvc.perform(put(URL + "/" + first.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("NEW12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value("NEW12345"));
        assertThat(packages.findById(first.getId()).orElseThrow().getTrackingCode()).isEqualTo("NEW12345");

        mockMvc.perform(put(URL + "/" + first.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("OTHER123")))
                .andExpect(status().isConflict());

        mockMvc.perform(put(URL + "/999999").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload("NEW12345")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Package not found"));
    }

    private String token() throws Exception {
        String response = mockMvc.perform(post("/api/cli/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"findmypacksadmin\",\"password\":\"findmypacksadmin\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(response).path("token").asText();
    }

    private String payload(String trackingCode) {
        return """
                {"trackingCode":"%s","customer":"Maria Silva","destination":"Fortaleza",
                 "carrier":"CORREIOS","status":"POSTED","postedAt":"%s"}
                """.formatted(trackingCode, LocalDate.now().minusDays(1));
    }

    private PackageRecord record(String trackingCode) {
        return new PackageRecord(trackingCode, "Maria Silva", "Fortaleza", "CORREIOS", "POSTED",
                LocalDate.now().minusDays(1));
    }
}
