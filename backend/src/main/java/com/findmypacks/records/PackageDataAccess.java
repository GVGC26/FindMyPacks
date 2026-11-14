package com.findmypacks.records;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PackageDataAccess {
    private final PackageRecordRepository packages;
    private final StructuredOperationLogger operationLogger;

    public PackageDataAccess(PackageRecordRepository packages, StructuredOperationLogger operationLogger) {
        this.packages = packages;
        this.operationLogger = operationLogger;
    }

    public PackageRecord insert(PackageRecord record) {
        return run("insert", "Package record persisted", "Package record insert failed",
                () -> packages.save(record));
    }

    public PackageRecord update(PackageRecord record) {
        return run("update", "Package record updated", "Package record update failed",
                () -> packages.save(record));
    }

    public PackageRecord insertForCli(PackageRecord record) {
        return run("insert", "Package record persisted", "Package record insert failed",
                () -> packages.save(record));
    }

    public PackageRecord updateForCli(PackageRecord record) {
        return run("update", "Package record updated", "Package record update failed",
                () -> packages.save(record));
    }

    public Optional<PackageRecord> findByIdForCli(Long id) {
        return run("find_package_by_id", "Package record read", "Package record read failed",
                () -> packages.findById(id));
    }

    public boolean existsByTrackingCodeForCli(String trackingCode) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCode(trackingCode));
    }

    public boolean existsByTrackingCodeAndIdNotForCli(String trackingCode, Long id) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCodeAndIdNot(trackingCode, id));
    }

    public Page<PackageRecord> findAllForCli(Pageable pageable) {
        return run("list", "Package records listed", "Package records list failed",
                () -> packages.findAll(pageable));
    }

    public void deleteByIdForCli(Long id) {
        run("delete", "Package record deleted", "Package record delete failed", () -> {
            packages.deleteById(id);
            return null;
        });
    }

    public Optional<PackageRecord> findById(Long id) {
        return run("find_package_by_id", "Package record read", "Package record read failed",
                () -> packages.findById(id));
    }

    public Page<PackageRecord> findAll(Pageable pageable) {
        return run("list", "Package records listed", "Package records list failed",
                () -> packages.findAll(pageable));
    }

    public Page<PackageRecord> search(String trackingCode, String customer, String destination,
                                      String carrier, String status, Pageable pageable) {
        return run("search_packages", "Package records searched", "Package records search failed",
                () -> packages.search(trackingCode, customer, destination, carrier, status, pageable));
    }

    public List<PackageRecord> exportAll(String trackingCode, String customer, String destination,
                                         String carrier, String status, Sort sort) {
        return run("export_packages", "Package records exported", "Package records export failed",
                () -> packages.exportRecords(trackingCode, customer, destination, carrier, status, sort));
    }

    public List<PackageRecord> exportAllForCli(String trackingCode, String customer, String destination,
                                               String carrier, String status, Sort sort) {
        return run("export_packages", "Package records exported", "Package records export failed",
                () -> packages.exportRecords(trackingCode, customer, destination, carrier, status, sort));
    }

    public void deleteById(Long id) {
        run("delete", "Package record deleted", "Package record delete failed", () -> {
            packages.deleteById(id);
            return null;
        });
    }

    public boolean existsByTrackingCode(String trackingCode) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCode(trackingCode));
    }

    public boolean existsByTrackingCodeAndIdNot(String trackingCode, Long id) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCodeAndIdNot(trackingCode, id));
    }

    public DashboardSummary dashboardSummary(LocalDate from, LocalDate to) {
        return run("dashboard_summary", "Package dashboard summary read", "Package dashboard summary read failed",
                () -> {
                    Map<LocalDate, Long> countByDate = packages.countPostedBetween(from, to).stream()
                            .collect(Collectors.toMap(DashboardDayCount::date, DashboardDayCount::count));
                    List<DashboardDayCount> postedPerDay = from.datesUntil(to.plusDays(1))
                            .map(date -> new DashboardDayCount(date, countByDate.getOrDefault(date, 0L)))
                            .toList();
                    return new DashboardSummary(
                            packages.countByStatus(),
                            packages.countByCarrier(),
                            postedPerDay);
                });
    }

    public DashboardSummary dashboardSummaryForLastSevenDays() {
        LocalDate today = LocalDate.now();
        return dashboardSummary(today.minusDays(6), today);
    }

    private <T> T run(String operation, String successMessage, String errorMessage, Supplier<T> action) {
        try {
            T result = action.get();
            operationLogger.log(Level.INFO, "db", operation, "success", successMessage);
            return result;
        } catch (RuntimeException exception) {
            operationLogger.log(Level.ERROR, "db", operation, "error", errorMessage);
            throw exception;
        }
    }
}
