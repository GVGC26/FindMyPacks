package com.findmypacks.records;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageListingDataAccessTest {
    private final PackageRecordRepository repository = mock(PackageRecordRepository.class);
    private final StructuredOperationLogger operationLogger = mock(StructuredOperationLogger.class);
    private final PackageDataAccess dataAccess = new PackageDataAccess(repository, operationLogger);
    private final PackageRecord record = mock(PackageRecord.class);

    @BeforeEach
    void setLoggingContext() {
        MDC.put("request_id", "package-listing-data-access-test");
        MDC.put("client", "web");
    }

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void listsPackagesAndLogsSuccessfulDatabaseRead() {
        var pageable = PageRequest.of(0, 5);
        var page = new PageImpl<>(List.of(record), pageable, 1);
        when(repository.findAll(pageable)).thenReturn(page);

        assertThat(dataAccess.findAll(pageable)).isSameAs(page);
        verify(operationLogger).log(Level.INFO, "db", "list", "success", "Package records listed");
    }

    @Test
    void logsListingFailureAndRethrowsIt() {
        var pageable = PageRequest.of(0, 5);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.findAll(pageable)).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.findAll(pageable)).isSameAs(failure);
        verify(operationLogger).log(Level.ERROR, "db", "list", "error", "Package records list failed");
    }

    @Test
    void searchesPackagesAndLogsSuccessfulDatabaseRead() {
        var pageable = PageRequest.of(0, 5);
        var page = new PageImpl<>(List.of(record), pageable, 1);
        when(repository.search("CODE", "Ana", null, null, null, pageable)).thenReturn(page);

        assertThat(dataAccess.search("CODE", "Ana", null, null, null, pageable)).isSameAs(page);
        verify(operationLogger).log(Level.INFO, "db", "search_packages", "success", "Package records searched");
    }

    @Test
    void logsSearchFailureAndRethrowsIt() {
        var pageable = PageRequest.of(0, 5);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.search(null, null, null, null, null, pageable)).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.search(null, null, null, null, null, pageable)).isSameAs(failure);
        verify(operationLogger).log(Level.ERROR, "db", "search_packages", "error", "Package records search failed");
    }

    @Test
    void deletesPackageAndLogsSuccessfulDatabaseWrite() {
        dataAccess.deleteById(7L);

        verify(repository).deleteById(7L);
        verify(operationLogger).log(Level.INFO, "db", "delete", "success", "Package record deleted");
    }

    @Test
    void logsDeleteFailureAndRethrowsIt() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        org.mockito.Mockito.doThrow(failure).when(repository).deleteById(7L);

        assertThatThrownBy(() -> dataAccess.deleteById(7L)).isSameAs(failure);
        verify(operationLogger).log(Level.ERROR, "db", "delete", "error", "Package record delete failed");
    }
}
