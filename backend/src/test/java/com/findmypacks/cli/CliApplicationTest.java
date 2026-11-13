package com.findmypacks.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliApplicationTest {
    private final CliApplication cli = new CliApplication();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(output);
    private final PrintStream err = new PrintStream(errors);

    @Test
    void mainAndHelpCommandsPrintUsage() {
        CliApplication.main(new String[] {"help"});
        assertThat(cli.run(new String[0], Map.of(), out, err, mock(HttpClient.class))).isZero();
        assertThat(cli.run(new String[] {"--help"}, Map.of(), out, err, mock(HttpClient.class))).isZero();
        assertThat(cli.run(new String[] {"-h"}, Map.of(), out, err, mock(HttpClient.class))).isZero();
        assertThat(output.toString()).contains("FindMyPacks CLI", "health", "FINDMYPACKS_TOKEN");
    }

    @Test
    void healthPrintsSuccessfulResponse() throws Exception {
        HttpClient client = clientReturning(200, "{\"status\":\"UP\"}");
        int code = cli.run(new String[] {"health"}, Map.of("FINDMYPACKS_API_URL", "http://api"), out, err, client);
        assertThat(code).isZero();
        assertThat(output.toString()).contains("UP");
    }

    @Test
    void tokenValidatesArgumentsAndEscapesJson() throws Exception {
        assertThat(cli.run(new String[] {"token"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Usage: token");
        HttpClient client = clientReturning(200, "{\"token\":\"abc\"}");
        assertThat(cli.run(new String[] {"token", "user\\name", "p\"ass"}, Map.of(), out, err, client)).isZero();
        assertThat(output.toString()).contains("abc");
    }

    @Test
    void protectedCommandRequiresEnvironmentToken() throws Exception {
        assertThat(cli.run(new String[] {"status"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"status"}, Map.of("FINDMYPACKS_TOKEN", " "), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Set FINDMYPACKS_TOKEN");
        HttpClient client = clientReturning(200, "{\"status\":\"ACTIVE\"}");
        assertThat(cli.run(new String[] {"status"}, Map.of("FINDMYPACKS_TOKEN", "abc"), out, err, client)).isZero();
        assertThat(output.toString()).contains("ACTIVE");
    }

    @Test
    void reportsHttpConnectionInterruptionAndUnknownCommand() throws Exception {
        assertThat(cli.run(new String[] {"unknown"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Unknown command");
        HttpClient rejected = clientReturning(401, "denied");
        assertThat(cli.run(new String[] {"health"}, Map.of(), out, err, rejected)).isOne();
        assertThat(errors.toString()).contains("HTTP 401");
        HttpClient unavailable = mock(HttpClient.class);
        when(unavailable.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("offline"));
        assertThat(cli.run(new String[] {"health"}, Map.of(), out, err, unavailable)).isOne();
        assertThat(errors.toString()).contains("Unable to connect", "offline");
        HttpClient interrupted = mock(HttpClient.class);
        when(interrupted.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        assertThat(cli.run(new String[] {"health"}, Map.of(), out, err, interrupted)).isOne();
        assertThat(Thread.interrupted()).isTrue();
        assertThat(errors.toString()).contains("interrupted");
    }

    @SuppressWarnings("unchecked")
    private HttpClient clientReturning(int status, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }
}
