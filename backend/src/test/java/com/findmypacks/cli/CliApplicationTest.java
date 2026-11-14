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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class CliApplicationTest {
    @TempDir Path temporaryDirectory;
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
    void searchRequiresOptionsAndSendsEncodedCombinableFiltersToCliApi() throws Exception {
        assertThat(cli.run(new String[] {"search"}, Map.of(), out, err, mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"search", "--unknown", "value"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"search", "--customer"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"search", "--customer", " "}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Usage: search");

        HttpClient client = clientReturning(200, "{\"content\":[]}");
        assertThat(cli.run(new String[] {"search", "--customer", "Maria Silva",
                        "--destination", "São Paulo", "--status", "IN_TRANSIT",
                        "--page", "1", "--size", "1", "--sort", "customer", "--direction", "asc"},
                Map.of("FINDMYPACKS_TOKEN", "abc", "FINDMYPACKS_API_URL", "http://api"),
                out, err, client)).isZero();
        ArgumentCaptor<java.net.http.HttpRequest> request = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/api/cli/records/search");
        assertThat(request.getValue().uri().getRawQuery())
                .isEqualTo("page=1&size=1&sort=customer%2Casc&customer=Maria+Silva"
                        + "&destination=S%C3%A3o+Paulo&status=IN_TRANSIT");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer abc");
    }

    @Test
    void exportValidatesOptionsAndWritesAllReturnedCsvToConfiguredFile() throws Exception {
        assertThat(cli.run(new String[] {"export", "--customer"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"export", "--unknown", "value"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(cli.run(new String[] {"export", "--customer", " "}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Usage: export");

        assertThat(cli.run(new String[] {"export"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Set FINDMYPACKS_TOKEN");

        Path destination = temporaryDirectory.resolve("nested/pacotes.csv");
        HttpClient client = clientReturning(200, "trackingCode,customer\r\nABC12345,Maria");
        assertThat(cli.run(new String[] {"export", "--customer", "Maria Silva",
                        "--status", "IN_TRANSIT", "--sort", "customer", "--direction", "asc"},
                Map.of("FINDMYPACKS_TOKEN", "abc", "FINDMYPACKS_API_URL", "http://api",
                        "FINDMYPACKS_EXPORT_FILE", destination.toString()), out, err, client)).isZero();
        assertThat(Files.readString(destination)).contains("ABC12345,Maria");
        assertThat(output.toString()).contains("CSV saved to", "pacotes.csv");

        ArgumentCaptor<java.net.http.HttpRequest> request = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().getPath()).isEqualTo("/api/cli/records/export");
        assertThat(request.getValue().uri().getRawQuery())
                .isEqualTo("sort=customer%2Casc&customer=Maria+Silva&status=IN_TRANSIT");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer abc");
    }

    @Test
    void exportUsesDefaultFileAndReportsApiErrors() throws Exception {
        String previousDirectory = System.getProperty("user.dir");
        System.setProperty("user.dir", temporaryDirectory.toString());
        try {
            HttpClient success = clientReturning(200, "trackingCode,customer");
            assertThat(cli.run(new String[] {"export"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                    out, err, success)).isZero();
            Files.deleteIfExists(Path.of("pacotes.csv"));
        } finally {
            System.setProperty("user.dir", previousDirectory);
        }

        HttpClient unauthorized = clientReturning(401, "denied");
        assertThat(cli.run(new String[] {"export"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, unauthorized)).isOne();
        assertThat(errors.toString()).contains("HTTP 401");
    }

    @Test
    void indicatorsRequireTokenAndPrintDashboardValuesAsText() throws Exception {
        assertThat(cli.run(new String[] {"indicators", "extra"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Usage: indicators");
        errors.reset();

        assertThat(cli.run(new String[] {"indicators"}, Map.of(), out, err,
                mock(HttpClient.class))).isEqualTo(2);
        assertThat(errors.toString()).contains("Set FINDMYPACKS_TOKEN");

        String body = """
                {
                  "byStatus":[{"label":"POSTED","count":2}],
                  "byCarrier":[{"label":"CORREIOS","count":1}],
                  "postedPerDay":[
                    {"date":"2026-11-08","count":0},
                    {"date":"2026-11-09","count":0},
                    {"date":"2026-11-10","count":0},
                    {"date":"2026-11-11","count":0},
                    {"date":"2026-11-12","count":0},
                    {"date":"2026-11-13","count":0},
                    {"date":"2026-11-14","count":3}
                  ]
                }
                """;
        HttpClient client = clientReturning(200, body);
        assertThat(cli.run(new String[] {"indicators"},
                Map.of("FINDMYPACKS_TOKEN", "abc", "FINDMYPACKS_API_URL", "http://api"),
                out, err, client)).isZero();
        assertThat(output.toString())
                .contains("Indicadores do dashboard de pacotes")
                .contains("Pacotes por status:")
                .contains("  Postado: 2")
                .contains("  Em trânsito: 0")
                .contains("Pacotes por transportadora:")
                .contains("  Correios: 1")
                .contains("  Outra: 0")
                .contains("Postagens nos últimos 7 dias:")
                .contains("  14/11/2026: 3")
                .doesNotContain("\"byStatus\"");
        ArgumentCaptor<java.net.http.HttpRequest> request = ArgumentCaptor.forClass(java.net.http.HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().method()).isEqualTo("GET");
        assertThat(request.getValue().uri().toString()).isEqualTo("http://api/api/cli/dashboard");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer abc");
    }

    @Test
    void indicatorsReportApiAndMalformedResponseErrors() throws Exception {
        HttpClient unauthorized = clientReturning(401, "denied");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, unauthorized)).isOne();
        assertThat(errors.toString()).contains("HTTP 401");
        errors.reset();

        HttpClient malformed = clientReturning(200, "not json");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, malformed)).isOne();
        assertThat(errors.toString()).contains("invalid dashboard response");
        errors.reset();

        HttpClient invalidCounts = clientReturning(200,
                "{\"byStatus\":{},\"byCarrier\":[],\"postedPerDay\":[]}");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, invalidCounts)).isOne();
        errors.reset();

        HttpClient invalidCountItem = clientReturning(200,
                "{\"byStatus\":[null],\"byCarrier\":[],\"postedPerDay\":[]}");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, invalidCountItem)).isOne();
        errors.reset();

        HttpClient negativeCount = clientReturning(200,
                "{\"byStatus\":[{\"label\":\"POSTED\",\"count\":-1}],"
                        + "\"byCarrier\":[],\"postedPerDay\":[]}");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, negativeCount)).isOne();
        errors.reset();

        HttpClient incomplete = clientReturning(200,
                "{\"byStatus\":[],\"byCarrier\":[],\"postedPerDay\":[]}");
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, incomplete)).isOne();
        assertThat(errors.toString()).contains("invalid dashboard response");
        errors.reset();

        String invalidDate = "{\"byStatus\":[],\"byCarrier\":[],\"postedPerDay\":["
                + "{\"date\":\"not-a-date\",\"count\":0},"
                + "{\"date\":\"2026-11-09\",\"count\":0},"
                + "{\"date\":\"2026-11-10\",\"count\":0},"
                + "{\"date\":\"2026-11-11\",\"count\":0},"
                + "{\"date\":\"2026-11-12\",\"count\":0},"
                + "{\"date\":\"2026-11-13\",\"count\":0},"
                + "{\"date\":\"2026-11-14\",\"count\":0}]}";
        HttpClient invalidDateClient = clientReturning(200, invalidDate);
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, invalidDateClient)).isOne();
        assertThat(errors.toString()).contains("invalid dashboard response");
        errors.reset();

        String nonTextDate = invalidDate.replace("\"not-a-date\"", "20261108");
        HttpClient nonTextDateClient = clientReturning(200, nonTextDate);
        assertThat(cli.run(new String[] {"indicators"}, Map.of("FINDMYPACKS_TOKEN", "abc"),
                out, err, nonTextDateClient)).isOne();
        assertThat(errors.toString()).contains("invalid dashboard response");
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
