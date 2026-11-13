package com.findmypacks.records;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "packages")
public class PackageRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 20)
    private String trackingCode;

    @Column(nullable = false, length = 100)
    private String customer;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false, length = 20)
    private String carrier;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "posted_at", nullable = false)
    private LocalDate postedAt;

    protected PackageRecord() {
    }

    public PackageRecord(String trackingCode, String customer, String destination, String carrier, String status, LocalDate postedAt) {
        this.trackingCode = trackingCode;
        this.customer = customer;
        this.destination = destination;
        this.carrier = carrier;
        this.status = status;
        this.postedAt = postedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public String getCustomer() {
        return customer;
    }

    public String getDestination() {
        return destination;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPostedAt() {
        return postedAt;
    }

    public void update(String trackingCode, String customer, String destination,
                       String carrier, String status, LocalDate postedAt) {
        this.trackingCode = trackingCode;
        this.customer = customer;
        this.destination = destination;
        this.carrier = carrier;
        this.status = status;
        this.postedAt = postedAt;
    }
}
