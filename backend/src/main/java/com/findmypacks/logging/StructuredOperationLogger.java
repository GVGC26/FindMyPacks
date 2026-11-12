package com.findmypacks.logging;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class StructuredOperationLogger {
    public enum Level {
        INFO,
        WARN,
        ERROR
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredOperationLogger.class);
    private static final JsonStringEncoder JSON_ENCODER = JsonStringEncoder.getInstance();

    public void log(Level level, String layer, String operation, String status, String message) {
        String requestId = MDC.get("request_id");
        String client = MDC.get("client");
        if (requestId == null || client == null) {
            throw new IllegalStateException("Operation logging context is missing");
        }

        String event = "{"
                + field("timestamp", Instant.now().toString()) + ","
                + field("level", level.name()) + ","
                + field("layer", layer) + ","
                + field("client", client) + ","
                + field("operation", operation) + ","
                + field("status", status) + ","
                + field("request_id", requestId) + ","
                + field("message", message)
                + "}";

        switch (level) {
            case INFO -> LOGGER.info(event);
            case WARN -> LOGGER.warn(event);
            case ERROR -> LOGGER.error(event);
        }
    }

    private String field(String name, String value) {
        return quote(name) + ":" + quote(value);
    }

    private String quote(String value) {
        return "\"" + new String(JSON_ENCODER.quoteAsString(value)) + "\"";
    }
}
