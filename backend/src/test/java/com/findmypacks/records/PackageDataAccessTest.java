package com.findmypacks.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
    void supportsCliCreateAndUpdateOperations() {
        when(repository.save(record)).thenReturn(record);
        when(repository.findById(1L)).thenReturn(Optional.of(record));
        when(repository.existsByTrackingCode("CLI12345")).thenReturn(false);
        when(repository.existsByTrackingCodeAndIdNot("CLI12345", 1L)).thenReturn(false);

        assertThat(dataAccess.insertForCli(record)).isSameAs(record);
        assertThat(dataAccess.updateForCli(record)).isSameAs(record);
        assertThat(dataAccess.findByIdForCli(1L)).contains(record);
        assertThat(dataAccess.existsByTrackingCodeForCli("CLI12345")).isFalse();
        assertThat(dataAccess.existsByTrackingCodeAndIdNotForCli("CLI12345", 1L)).isFalse();
    }

    @Test
    void supportsCliListingAndDeletion() {
        Pageable pageable = mock(Pageable.class);
        @SuppressWarnings("unchecked")
        Page<PackageRecord> page = mock(Page.class);
        when(repository.findAll(pageable)).thenReturn(page);

        assertThat(dataAccess.findAllForCli(pageable)).isSameAs(page);
        dataAccess.deleteByIdForCli(1L);

        verify(repository).deleteById(1L);
        verify(operationLogger).log(Level.INFO, "db", "list", "success", "Package records listed");
        verify(operationLogger).log(Level.INFO, "db", "delete", "success", "Package record deleted");
    }

    @Test
    void logsAndRethrowsFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.save(record)).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.insert(record)).isSameAs(failure);
        verify(operationLogger).log(Level.ERROR, "db", "insert", "error", "Package record insert failed");
    }

    @Test
    void buildsDashboardSummaryWithZeroCountDates() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate middle = from.plusDays(1);
        LocalDate to = from.plusDays(2);
        when(repository.countPostedBetween(from, to))
                .thenReturn(List.of(new DashboardDayCount(middle, 2L)));
        when(repository.countByStatus()).thenReturn(List.of(new DashboardCount("POSTED", 2L)));
        when(repository.countByCarrier()).thenReturn(List.of(new DashboardCount("CORREIOS", 2L)));

        DashboardSummary result = dataAccess.dashboardSummary(from, to);

        assertThat(result.byStatus()).containsExactly(new DashboardCount("POSTED", 2L));
        assertThat(result.byCarrier()).containsExactly(new DashboardCount("CORREIOS", 2L));
        assertThat(result.postedPerDay()).containsExactly(
                new DashboardDayCount(from, 0L),
                new DashboardDayCount(middle, 2L),
                new DashboardDayCount(to, 0L));
        verify(operationLogger).log(
                Level.INFO, "db", "dashboard_summary", "success", "Package dashboard summary read");
    }

    @Test
    void logsAndRethrowsDashboardQueryFailures() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = from.plusDays(6);
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.countPostedBetween(from, to)).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.dashboardSummary(from, to)).isSameAs(failure);
        verify(operationLogger).log(
                Level.ERROR, "db", "dashboard_summary", "error", "Package dashboard summary read failed");
    }

    @Test
    void sharesTheLastSevenDayDashboardWindow() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        when(repository.countPostedBetween(from, today)).thenReturn(List.of());
        when(repository.countByStatus()).thenReturn(List.of());
        when(repository.countByCarrier()).thenReturn(List.of());

        DashboardSummary result = dataAccess.dashboardSummaryForLastSevenDays();

        assertThat(result.postedPerDay()).hasSize(7);
        assertThat(result.postedPerDay().get(0).date()).isEqualTo(from);
        assertThat(result.postedPerDay().get(6).date()).isEqualTo(today);
        verify(repository).countPostedBetween(from, today);
    }
}
