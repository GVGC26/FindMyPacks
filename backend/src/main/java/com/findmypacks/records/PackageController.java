package com.findmypacks.records;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/web/records")
public class PackageController {
    private final PackageDataAccess packages;

    public PackageController(PackageDataAccess packages) {
        this.packages = packages;
    }

    @GetMapping
    public Page<PackageResponse> list(Pageable pageable) {
        return packages.findAll(pageable).map(PackageResponse::from);
    }

    @GetMapping("/search")
    public Page<PackageResponse> search(
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "postedAt,desc") String sort) {
        validateChoice("carrier", carrier, java.util.Set.of("CORREIOS", "JADLOG", "LOGGI", "OTHER"));
        validateChoice("status", status, java.util.Set.of("POSTED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED"));
        Pageable pageable = searchPageable(page, size, sort);
        return packages.search(normalizeText(trackingCode), normalizeText(customer), normalizeText(destination),
                normalizeChoice(carrier), normalizeChoice(status), pageable).map(PackageResponse::from);
    }

    private Pageable searchPageable(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paging parameters");
        }
        String[] parts = sort.split(",", -1);
        java.util.Set<String> sortable = java.util.Set.of("trackingCode", "customer", "destination", "postedAt");
        if (parts.length < 1 || parts.length > 2 || !sortable.contains(parts[0])) {
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private String normalizeChoice(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeText(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private void validateChoice(String field, String value, java.util.Set<String> allowed) {
        if (value != null && !value.isBlank()
                && !allowed.contains(value.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + " filter");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (packages.findById(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
        }
        packages.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PackageResponse> create(@Valid @RequestBody PackageRequest request) {
        if (packages.existsByTrackingCode(request.trackingCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tracking code already registered");
        }
        PackageRecord saved = packages.insert(new PackageRecord(
                request.trackingCode(), request.customer(), request.destination(),
                request.carrier(), request.status(), request.postedAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PackageResponse.from(saved));
    }

    @PutMapping("/{id}")
    public PackageResponse update(@PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        PackageRecord record = packages.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
        if (packages.existsByTrackingCodeAndIdNot(request.trackingCode(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tracking code already registered");
        }
        record.update(request.trackingCode(), request.customer(), request.destination(),
                request.carrier(), request.status(), request.postedAt());
        return PackageResponse.from(packages.update(record));
    }
}
