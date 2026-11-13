package com.findmypacks.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.mockito.ArgumentCaptor;
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
    void createAndUpdateValidateArgumentsTokenAndSendRequests() throws Exception {
        assertThat(cli.run(new String[] {"create"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"update"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        String[] create = {"create", "ABC12345", "Maria Silva", "Fortaleza", "CORREIOS", "POSTED", "2026-11-12"};
        assertThat(cli.run(create, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);

        HttpClient createClient = clientReturning(201, "{\"id\":1}");
        assertThat(cli.run(create, Map.of("FINDMYPACKS_TOKEN", "abc"), out, err, createClient)).isZero();
        ArgumentCaptor<java.net.http.HttpRequest> createRequest = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(createClient).send(createRequest.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(createRequest.getValue().method()).isEqualTo("POST");
        assertThat(createRequest.getValue().uri().getPath()).isEqualTo("/api/cli/records");
        assertThat(createRequest.getValue().headers().firstValue("Authorization")).contains("Bearer abc");

        String[] update = {"update", "1", "NEW12345", "Maria Silva", "Recife", "JADLOG", "IN_TRANSIT", "2026-11-12"};
        HttpClient updateClient = clientReturning(200, "{\"id\":1}");
        assertThat(cli.run(update, Map.of("FINDMYPACKS_TOKEN", "abc"), out, err, updateClient)).isZero();
        ArgumentCaptor<java.net.http.HttpRequest> updateRequest = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(updateClient).send(updateRequest.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(updateRequest.getValue().method()).isEqualTo("PUT");
        assertThat(updateRequest.getValue().uri().getPath()).isEqualTo("/api/cli/records/1");
    }

    @Test
    void listAndDeleteValidateArgumentsTokenAndSendRequests() throws Exception {
        assertThat(cli.run(new String[] {"list", "0", "postedAt", "desc", "extra"},
                Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"delete"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"list"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);

        HttpClient listClient = clientReturning(200, "{\"content\":[]}");
        assertThat(cli.run(new String[] {"list", "1", "trackingCode", "asc"},
                Map.of("FINDMYPACKS_TOKEN", "abc"), out, err, listClient)).isZero();
        ArgumentCaptor<java.net.http.HttpRequest> listRequest = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(listClient).send(listRequest.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(listRequest.getValue().method()).isEqualTo("GET");
        assertThat(listRequest.getValue().uri().getQuery())
                .isEqualTo("page=1&size=5&sort=trackingCode,asc");

        HttpClient deleteClient = clientReturning(204, "");
        assertThat(cli.run(new String[] {"delete", "7"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, deleteClient)).isZero();
        ArgumentCaptor<java.net.http.HttpRequest> deleteRequest = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(deleteClient).send(deleteRequest.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(deleteRequest.getValue().method()).isEqualTo("DELETE");
        assertThat(deleteRequest.getValue().uri().getPath()).isEqualTo("/api/cli/records/7");
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
