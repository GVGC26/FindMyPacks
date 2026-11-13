package com.findmypacks.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PackageRequest(
        @NotBlank(message = "Tracking code is required")
        @Pattern(regexp = "^[A-Za-z0-9]{8,20}$", message = "Tracking code must be 8 to 20 letters or digits")
        String trackingCode,

        @NotBlank(message = "Customer is required")
        @Size(min = 2, max = 100, message = "Customer must have 2 to 100 characters")
        String customer,

        @NotBlank(message = "Destination is required")
        @Size(min = 2, max = 100, message = "Destination must have 2 to 100 characters")
        String destination,

        @NotBlank(message = "Carrier is required")
        @Pattern(regexp = "^(CORREIOS|JADLOG|LOGGI|OTHER)$", message = "Invalid carrier")
        String carrier,

        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(POSTED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED)$", message = "Invalid status")
        String status,

        @NotNull(message = "Posted date is required")
        @PastOrPresent(message = "Posted date cannot be in the future")
        LocalDate postedAt) {
}