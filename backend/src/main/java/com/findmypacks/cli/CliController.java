package com.findmypacks.cli;

import com.findmypacks.user.AppUserDataAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;

@RestController
@RequestMapping("/api/cli")
public class CliController {
    private final AuthenticationManager authenticationManager;
    private final TokenRegistry tokens;
    private final AppUserDataAccess users;

    public CliController(AuthenticationManager authenticationManager, TokenRegistry tokens, AppUserDataAccess users) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
        this.users = users;
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
