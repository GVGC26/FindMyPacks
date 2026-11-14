package com.findmypacks.cli;

import com.findmypacks.records.DashboardSummary;
import com.findmypacks.records.PackageDataAccess;
import com.findmypacks.records.PackageRecord;
import com.findmypacks.records.PackageRequest;
import com.findmypacks.records.PackageResponse;
import com.findmypacks.records.PackageSearchParameters;
import com.findmypacks.user.AppUserDataAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;

@RestController
@RequestMapping("/api/cli")
public class CliController {
    private final AuthenticationManager authenticationManager;
    private final TokenRegistry tokens;
    private final AppUserDataAccess users;
    private final PackageDataAccess packages;

    public CliController(
            AuthenticationManager authenticationManager,
            TokenRegistry tokens,
            AppUserDataAccess users,
            PackageDataAccess packages) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.users = users;
        this.packages = packages;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest body) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        return new TokenResponse(tokens.issue(authentication.getName()), "Bearer");
    }

    @GetMapping("/status")
    public StatusResponse status(Authentication authentication) {
        boolean exists = users.findByUsername(authentication.getName()).isPresent();
        return new StatusResponse(authentication.getName(), exists ? "ACTIVE" : "MISSING");
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return packages.dashboardSummaryForLastSevenDays();
    }

    @PostMapping("/records")
    public ResponseEntity<PackageResponse> create(@Valid @RequestBody PackageRequest request) {
        if (packages.existsByTrackingCodeForCli(request.trackingCode())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.CONFLICT, "Tracking code already registered");
        }
        PackageRecord saved = packages.insertForCli(record(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(PackageResponse.from(saved));
    }

    @PutMapping("/records/{id}")
    public PackageResponse update(@PathVariable Long id, @Valid @RequestBody PackageRequest request) {
        PackageRecord record = packages.findByIdForCli(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Package not found"));
        if (packages.existsByTrackingCodeAndIdNotForCli(request.trackingCode(), id)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.CONFLICT, "Tracking code already registered");
        }
        record.update(request.trackingCode(), request.customer(), request.destination(),
                request.carrier(), request.status(), request.postedAt());
        return PackageResponse.from(packages.updateForCli(record));
    }

    @GetMapping("/records")
    public Page<PackageResponse> list(Pageable pageable) {
        return packages.findAllForCli(pageable).map(PackageResponse::from);
    }

    @GetMapping("/records/search")
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

    @GetMapping(value = "/records/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "postedAt,desc") String sort) {
        PackageSearchParameters parameters = PackageSearchParameters.from(
                trackingCode, customer, destination, carrier, status, 0, 1, sort);
        List<PackageRecord> records = packages.exportAllForCli(parameters.trackingCode(), parameters.customer(),
                parameters.destination(), parameters.carrier(), parameters.status(), parameters.pageable().getSort());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"pacotes.csv\"")
                .body(csv(records));
    }

    private String csv(List<PackageRecord> records) {
        StringBuilder csv = new StringBuilder("trackingCode,customer,destination,carrier,status,postedAt");
        for (PackageRecord record : records) {
            csv.append("\r\n").append(String.join(",",
                    csvCell(record.getTrackingCode()), csvCell(record.getCustomer()),
                    csvCell(record.getDestination()), csvCell(record.getCarrier()),
                    csvCell(record.getStatus()), csvCell(record.getPostedAt().toString())));
        }
        return csv.toString();
    }

    private String csvCell(String value) {
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\r") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (packages.findByIdForCli(id).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Package not found");
        }
        packages.deleteByIdForCli(id);
        return ResponseEntity.noContent().build();
    }

    private PackageRecord record(PackageRequest request) {
        return new PackageRecord(request.trackingCode(), request.customer(), request.destination(),
                request.carrier(), request.status(), request.postedAt());
    }

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String token, String type) {
    }

    public record StatusResponse(String username, String status) {
    }

    @Component
    public static class TokenRegistry {
        private final Map<String, String> tokens = new ConcurrentHashMap<>();

        String issue(String username) {
            String token = UUID.randomUUID().toString();
            tokens.put(token, username);
            return token;
        }

        Optional<String> usernameFor(String token) {
            return Optional.ofNullable(tokens.get(token));
        }
    }

    @Component
    public static class BearerFilter extends OncePerRequestFilter {
        private final TokenRegistry tokens;

        public BearerFilter(TokenRegistry tokens) {
            this.tokens = tokens;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            return !path.startsWith("/api/cli/")
                    || path.equals("/api/cli/health")
                    || path.equals("/api/cli/token");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                tokens.usernameFor(authorization.substring(7)).ifPresent(username ->
                        SecurityContextHolder.getContext().setAuthentication(
                                UsernamePasswordAuthenticationToken.authenticated(username, null, java.util.List.of())));
            }
            chain.doFilter(request, response);
        }
    }
}

@RestControllerAdvice(assignableTypes = CliController.class)
class CliExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid username or password"));
    }
}
