package com.findmypacks.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    private final StructuredOperationLogger operationLogger = mock(StructuredOperationLogger.class);
    private final RequestIdFilter filter = new RequestIdFilter(operationLogger);

    @AfterEach
    void clearLoggingContext() {
        MDC.clear();
    }

    @Test
    void logsServerFailuresAndClearsRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/web/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "server-error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, (servletRequest, servletResponse) -> {
            throw new IOException("request failed");
        })).isInstanceOf(IOException.class);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("client")).isNull();
        verify(operationLogger).log(
                Level.ERROR, "api", "health", "error", "Web operation completed");
    }
}
