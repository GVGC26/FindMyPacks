package com.findmypacks.records;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.util.Optional;
import java.util.function.Supplier;
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

    public Optional<PackageRecord> findById(Long id) {
        return run("find_package_by_id", "Package record read", "Package record read failed",
                () -> packages.findById(id));
    }

    public boolean existsByTrackingCode(String trackingCode) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCode(trackingCode));
    }

    public boolean existsByTrackingCodeAndIdNot(String trackingCode, Long id) {
        return run("check_tracking_code", "Tracking code checked", "Tracking code check failed",
                () -> packages.existsByTrackingCodeAndIdNot(trackingCode, id));
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