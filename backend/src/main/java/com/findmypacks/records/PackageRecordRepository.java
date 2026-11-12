package com.findmypacks.records;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PackageRecordRepository extends JpaRepository<PackageRecord, Long> {

    @Query("select (count(p) > 0) from PackageRecord p where p.trackingCode = ?1")
    boolean existsByTrackingCode(String trackingCode);

    @Query("select (count(p) > 0) from PackageRecord p where p.trackingCode = ?1 and p.id <> ?2")
    boolean existsByTrackingCodeAndIdNot(String trackingCode, Long id);
}
