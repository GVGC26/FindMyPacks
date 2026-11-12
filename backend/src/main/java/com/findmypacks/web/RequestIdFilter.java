package com.findmypacks.web;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private final StructuredOperationLogger operationLogger;

    public RequestIdFilter(StructuredOperationLogger operationLogger) {
        this.operationLogger = operationLogger;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestIdFrom(request);
        MDC.put("request_id", requestId);
        MDC.put("client", "web");
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            throw exception;
        } finally {
            try {
                int statusCode = response.getStatus();
                String status = statusCode >= 400 ? "error" : "success";
                Level level = statusCode >= 500 ? Level.ERROR : statusCode >= 400 ? Level.WARN : Level.INFO;
                operationLogger.log(level, "api", operationFor(request), status, "Web operation completed");
            } finally {
                MDC.remove("request_id");
                MDC.remove("client");
            }
        }
    }

    private String operationFor(HttpServletRequest request) {
        return switch (request.getRequestURI()) {
            case "/api/web/health" -> "health";
            case "/api/web/csrf" -> "csrf";
            case "/api/web/login" -> "login";
            default -> request.getMethod().toLowerCase(Locale.ROOT) + "_" + request.getRequestURI();
        };
    }

    private String requestIdFrom(HttpServletRequest request) {
        String candidate = request.getHeader(HEADER_NAME);
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
