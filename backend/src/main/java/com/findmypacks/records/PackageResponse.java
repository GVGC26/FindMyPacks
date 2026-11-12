package com.findmypacks.records;

import java.time.LocalDate;

public record PackageResponse(
        Long id,
        String trackingCode,
        String customer,
        String destination,
        String carrier,
        String status,
        LocalDate postedAt) {

    public static PackageResponse from(PackageRecord record) {
        return new PackageResponse(
                record.getId(),
                record.getTrackingCode(),
                record.getCustomer(),
                record.getDestination(),
                record.getCarrier(),
                record.getStatus(),
                record.getPostedAt());
    }
}