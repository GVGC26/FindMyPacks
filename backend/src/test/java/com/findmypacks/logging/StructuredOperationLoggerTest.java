package com.findmypacks.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class StructuredOperationLoggerTest {
    private final StructuredOperationLogger operationLogger = new StructuredOperationLogger();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger logger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void startAppender() {
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        MDC.put("request_id", "structured-log-test");
        MDC.put("client", "web");
    }

    @AfterEach
    void stopAppender() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    void writesJsonWithRequiredFieldsAndEscapesMessages() throws Exception {
        operationLogger.log(StructuredOperationLogger.Level.INFO,
                "api", "login", "success", "Message with \"quoted\" text");

        JsonNode event = objectMapper.readTree(appender.list.get(0).getFormattedMessage());
        assertThat(event.get("timestamp").asText()).isNotBlank();
        assertThat(event.get("level").asText()).isEqualTo("INFO");
        assertThat(event.get("layer").asText()).isEqualTo("api");
        assertThat(event.get("client").asText()).isEqualTo("web");
        assertThat(event.get("operation").asText()).isEqualTo("login");
        assertThat(event.get("status").asText()).isEqualTo("success");
        assertThat(event.get("request_id").asText()).isEqualTo("structured-log-test");
        assertThat(event.get("message").asText()).isEqualTo("Message with \"quoted\" text");
        assertThat(event.size()).isEqualTo(8);
    }

    @Test
    void writesWarnAndErrorAtTheirConfiguredLevels() {
        operationLogger.log(StructuredOperationLogger.Level.WARN, "api", "login", "error", "Login failed");
        operationLogger.log(StructuredOperationLogger.Level.ERROR, "db", "read", "error", "Read failed");

        assertThat(appender.list).extracting(ILoggingEvent::getLevel)
                .containsExactly(ch.qos.logback.classic.Level.WARN, ch.qos.logback.classic.Level.ERROR);
    }

    @Test
    void rejectsEventsWithoutRequestContext() {
        MDC.clear();

        assertThatThrownBy(() -> operationLogger.log(
                StructuredOperationLogger.Level.INFO, "api", "health", "success", "Ready"))
                .isInstanceOf(IllegalStateException.class);
    }
}
