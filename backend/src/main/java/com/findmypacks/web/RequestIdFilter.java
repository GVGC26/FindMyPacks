package com.findmypacks.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = requestIdFrom(request);
        MDC.put("request_id", requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.info("HTTP {} {} completed with status {}", request.getMethod(),
                    request.getRequestURI(), response.getStatus());
            MDC.remove("request_id");
        }
    }

    private String requestIdFrom(HttpServletRequest request) {
        String candidate = request.getHeader(HEADER_NAME);
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
