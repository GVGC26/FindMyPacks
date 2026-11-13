package com.findmypacks.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AppUserDataAccessTest {
    private final AppUserRepository repository = mock(AppUserRepository.class);
    private final StructuredOperationLogger operationLogger = mock(StructuredOperationLogger.class);
    private final AppUserDataAccess dataAccess = new AppUserDataAccess(repository, operationLogger);

    @BeforeEach
    void setLoggingContext() {
        MDC.put("request_id", "data-access-test");
        MDC.put("client", "web");
    }

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void logsSuccessfulReads() {
        AppUser user = mock(AppUser.class);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThat(dataAccess.findByUsername("admin")).contains(user);
        verify(operationLogger).log(Level.INFO, "db", "find_user_by_username", "success", "User record read");
    }

    @Test
    void logsAndRethrowsReadFailures() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(repository.findByUsername("admin")).thenThrow(failure);

        assertThatThrownBy(() -> dataAccess.findByUsername("admin")).isSameAs(failure);
        verify(operationLogger).log(
                Level.ERROR, "db", "find_user_by_username", "error", "User record read failed");
    }
}
