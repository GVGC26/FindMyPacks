package com.findmypacks.records;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageListingApiIntegrationTest {
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

    private PackageRecord savePackage(String trackingCode, String customer) {
        return packages.save(new PackageRecord(
                trackingCode, customer, "Fortaleza", "CORREIOS", "POSTED", LocalDate.now().minusDays(1)));
    }

    @Test
    void listsPackagesFromDatabaseWithFiveItemsPerPage() throws Exception {
        for (int index = 1; index <= 7; index++) {
            savePackage("CODE%05d".formatted(index), "Cliente %d".formatted(index));
        }

        mockMvc.perform(get(URL).param("page", "0").param("size", "5").with(admin()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.length()").value(5))
                        .andExpect(jsonPath("$.number").value(0))
                        .andExpect(jsonPath("$.size").value(5))
                        .andExpect(jsonPath("$.totalElements").value(7))
                        .andExpect(jsonPath("$.totalPages").value(2))
                        .andExpect(jsonPath("$.content[0].trackingCode").exists())
                        .andExpect(jsonPath("$.content[0].customer").exists())
                        .andExpect(jsonPath("$.content[0].destination").exists())
                        .andExpect(jsonPath("$.content[0].postedAt").exists());

        mockMvc.perform(get(URL).param("page", "1").param("size", "5").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void listsPackagesInRequestedSortOrder() throws Exception {
        savePackage("CODE00001", "Zelda");
        savePackage("CODE00002", "Ana");

        mockMvc.perform(get(URL).param("sort", "customer,asc").with(admin()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].customer").value("Ana"))
                        .andExpect(jsonPath("$.content[1].customer").value("Zelda"));
    }

    @Test
    void listingWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletesSelectedPackageFromDatabase() throws Exception {
        PackageRecord record = savePackage("CODE00001", "Ana");

        mockMvc.perform(delete(URL + "/" + record.getId()).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(packages.findById(record.getId())).isEmpty();
    }

    @Test
    void deletingUnknownPackageReturns404() throws Exception {
        mockMvc.perform(delete(URL + "/999999").with(admin()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Package not found"));
    }

    @Test
    void deletingWithoutAuthenticationReturns401AndKeepsPackage() throws Exception {
        PackageRecord record = savePackage("CODE00001", "Ana");

        mockMvc.perform(delete(URL + "/" + record.getId()).with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(packages.findById(record.getId())).isPresent();
    }
}
