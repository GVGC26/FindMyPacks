package com.findmypacks.records;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

public interface PackageRecordRepository extends JpaRepository<PackageRecord, Long> {

    @Query("select (count(p) > 0) from PackageRecord p where p.trackingCode = ?1")
    boolean existsByTrackingCode(String trackingCode);

    @Query("select (count(p) > 0) from PackageRecord p where p.trackingCode = ?1 and p.id <> ?2")
    boolean existsByTrackingCodeAndIdNot(String trackingCode, Long id);

    @Query("""
            select new com.findmypacks.records.DashboardCount(p.status, count(p))
            from PackageRecord p
            group by p.status
            order by p.status
            """)
    List<DashboardCount> countByStatus();

    @Query("""
            select new com.findmypacks.records.DashboardCount(p.carrier, count(p))
            from PackageRecord p
            group by p.carrier
            order by p.carrier
            """)
    List<DashboardCount> countByCarrier();

    @Query("""
            select new com.findmypacks.records.DashboardDayCount(p.postedAt, count(p))
            from PackageRecord p
            where p.postedAt between :from and :to
            group by p.postedAt
            order by p.postedAt
            """)
    List<DashboardDayCount> countPostedBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
