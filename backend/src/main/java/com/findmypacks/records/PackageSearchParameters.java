package com.findmypacks.records;

import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record PackageSearchParameters(
        String trackingCode,
        String customer,
        String destination,
        String carrier,
        String status,
        Pageable pageable) {
    private static final Set<String> CARRIERS = Set.of("CORREIOS", "JADLOG", "LOGGI", "OTHER");
    private static final Set<String> STATUSES = Set.of("POSTED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
    private static final Set<String> SORTABLE_FIELDS = Set.of("trackingCode", "customer", "destination", "postedAt");

    public static PackageSearchParameters from(
            String trackingCode, String customer, String destination, String carrier, String status,
            int page, int size, String sort) {
        validateChoice("carrier", carrier, CARRIERS);
        validateChoice("status", status, STATUSES);
        return new PackageSearchParameters(
                normalizeText(trackingCode),
                normalizeText(customer),
                normalizeText(destination),
                normalizeChoice(carrier),
                normalizeChoice(status),
                pageable(page, size, sort));
    }

    private static Pageable pageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paging parameters");
        }
        String[] parts = sort.split(",", -1);
        if (parts.length > 2 || !SORTABLE_FIELDS.contains(parts[0])) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            if (!parts[1].equalsIgnoreCase("asc") && !parts[1].equalsIgnoreCase("desc")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort direction");
            }
            direction = Sort.Direction.fromString(parts[1]);
        }
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }

    private static void validateChoice(String field, String value, Set<String> allowed) {
        if (value != null && !value.isBlank() && !allowed.contains(value.trim().toUpperCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + " filter");
        }
    }

    private static String normalizeChoice(String value) {
        return normalize(value).toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
