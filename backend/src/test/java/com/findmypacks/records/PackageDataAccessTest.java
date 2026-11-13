package com.findmypacks.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class PackageDataAccessTest {
    private final PackageRecordRepository repository = mock(PackageRecordRepository.class);
    private final StructuredOperationLogger operationLogger = mock(StructuredOperationLogger.class);
    private final PackageDataAccess dataAccess = new PackageDataAccess(repository, operationLogger);
    private final PackageRecord record = mock(PackageRecord.class);

    @BeforeEach
    void setLoggingContext() {
        MDC.put("request_id", "package-data-access-test");
        MDC.put("client", "web");
    }

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void logsSuccessfulInsert() {
        when(repository.save(record)).thenReturn(record);

        assertThat(dataAccess.insert(record)).isSameAs(record);
        verify(operationLogger).log(Level.INFO, "db", "insert", "success", "Package record persisted");
    }

    @Test
    void logsSuccessfulUpdate() {
        when(repository.save(record)).thenReturn(record);

        assertThat(dataAccess.update(record)).isSameAs(record);
        verify(operationLogger).log(Level.INFO, "db", "update", "success", "Package record updated");
    }

    @Test
    void logsSuccessfulReads() {
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.existsByTrackingCode("ABC12345")).thenReturn(true);
        when(repository.existsByTrackingCodeAndIdNot("ABC12345", 1L)).thenReturn(false);

        assertThat(dataAccess.findById(1L)).contains(record);
        assertThat(dataAccess.existsByTrackingCode("ABC12345")).isTrue();
        assertThat(dataAccess.existsByTrackingCodeAndIdNot("ABC12345", 1L)).isFalse();
        verify(operationLogger).log(Level.INFO, "db", "find_package_by_id", "success", "Package record read");
    }

    @Test
    void logsAndRethrowsFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.save(record)).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.insert(record)).isSameAs(failure);
        verify(operationLogger).log(Level.ERROR, "db", "insert", "error", "Package record insert failed");
    }
}