package com.findmypacks.cli;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import com.findmypacks.user.AppUser;
import com.findmypacks.user.AppUserDataAccess;
import com.findmypacks.user.AppUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.DefaultApplicationArguments;

class AdminStatusCliRunnerTest {
    private final AppUserDataAccess users = mock(AppUserDataAccess.class);
    private final StructuredOperationLogger operationLogger = mock(StructuredOperationLogger.class);

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void logsSuccessfulAdminLookup() {
        when(users.findByUsername("findmypacksadmin")).thenReturn(Optional.of(mock(AppUser.class)));
        AdminStatusCliRunner runner = runner("admin-status");

        runner.run(new DefaultApplicationArguments());

        verify(operationLogger).log(
                Level.INFO, "api", "admin-status", "success", "Administrator exists");
    }

    @Test
    void logsCorrelatedCliAndDatabaseEvents() throws Exception {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.findByUsername("findmypacksadmin")).thenReturn(Optional.of(mock(AppUser.class)));
        StructuredOperationLogger logger = new StructuredOperationLogger();
        AppUserDataAccess dataAccess = new AppUserDataAccess(repository, logger);
        AdminStatusCliRunner runner = new AdminStatusCliRunner(dataAccess, logger, "findmypacksadmin", "admin-status");
        Logger structuredLogger = (Logger) LoggerFactory.getLogger(StructuredOperationLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        structuredLogger.addAppender(appender);
        try {
            runner.run(new DefaultApplicationArguments());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode apiEvent = null;
            JsonNode dbEvent = null;
            for (ILoggingEvent loggedEvent : appender.list) {
                JsonNode event = mapper.readTree(loggedEvent.getFormattedMessage());
                if ("api".equals(event.path("layer").asText())) {
                    apiEvent = event;
                }
                if ("db".equals(event.path("layer").asText())) {
                    dbEvent = event;
                }
            }

            assertThat(apiEvent).isNotNull();
            assertThat(dbEvent).isNotNull();
            assertThat(apiEvent.path("client").asText()).isEqualTo("cli");
            assertThat(dbEvent.path("client").asText()).isEqualTo("cli");
            assertThat(apiEvent.path("request_id").asText()).isNotBlank();
            assertThat(dbEvent.path("request_id").asText()).isEqualTo(apiEvent.path("request_id").asText());
            assertThat(apiEvent.path("status").asText()).isEqualTo("success");
            assertThat(dbEvent.path("status").asText()).isEqualTo("success");
        } finally {
            structuredLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsMissingAdminAsSuccessfulRead() {
        when(users.findByUsername("findmypacksadmin")).thenReturn(Optional.empty());
        AdminStatusCliRunner runner = runner("admin-status");

        runner.run(new DefaultApplicationArguments());

        verify(operationLogger).log(
                Level.INFO, "api", "admin-status", "success", "Administrator is missing");
    }

    @Test
    void logsAndRejectsUnsupportedOperations() {
        AdminStatusCliRunner runner = runner("unsupported");

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported CLI operation");
        verify(operationLogger).log(
                Level.ERROR, "api", "unsupported", "error", "CLI operation failed");
    }

    @Test
    void logsAndRethrowsDatabaseFailures() {
        when(users.findByUsername("findmypacksadmin"))
                .thenThrow(new IllegalStateException("database unavailable"));
        AdminStatusCliRunner runner = runner("admin-status");

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(operationLogger).log(
                Level.ERROR, "api", "admin-status", "error", "CLI operation failed");
    }

    private AdminStatusCliRunner runner(String operation) {
        return new AdminStatusCliRunner(users, operationLogger, "findmypacksadmin", operation);
    }
}
