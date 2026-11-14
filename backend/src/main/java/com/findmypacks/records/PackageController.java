package com.findmypacks.records;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
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
        PackageSearchParameters parameters = PackageSearchParameters.from(
                trackingCode, customer, destination, carrier, status, page, size, sort);
        return packages.search(parameters.trackingCode(), parameters.customer(), parameters.destination(),
                parameters.carrier(), parameters.status(), parameters.pageable()).map(PackageResponse::from);
    }

    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "postedAt,desc") String sort) {
        PackageSearchParameters parameters = PackageSearchParameters.from(
                trackingCode, customer, destination, carrier, status, 0, 1, sort);
        List<PackageRecord> records = packages.exportAll(parameters.trackingCode(), parameters.customer(),
                parameters.destination(), parameters.carrier(), parameters.status(), parameters.pageable().getSort());
        String csv = csv(records);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"pacotes.csv\"")
                .body(csv);
    }

    private String csv(List<PackageRecord> records) {
        StringBuilder csv = new StringBuilder("trackingCode,customer,destination,carrier,status,postedAt");
        for (PackageRecord record : records) {
            csv.append("\r\n").append(csvRow(record));
        }
        return csv.toString();
    }

    private String csvRow(PackageRecord record) {
        return String.join(",",
                csvCell(record.getTrackingCode()), csvCell(record.getCustomer()),
                csvCell(record.getDestination()), csvCell(record.getCarrier()),
                csvCell(record.getStatus()), csvCell(record.getPostedAt().toString()));
    }

    private String csvCell(String value) {
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\r") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
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
