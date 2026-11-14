package com.findmypacks.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CliApplication {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<Map.Entry<String, String>> STATUSES = List.of(
            Map.entry("POSTED", "Postado"),
            Map.entry("IN_TRANSIT", "Em trânsito"),
            Map.entry("OUT_FOR_DELIVERY", "Saiu para entrega"),
            Map.entry("DELIVERED", "Entregue"));
    private static final List<Map.Entry<String, String>> CARRIERS = List.of(
            Map.entry("CORREIOS", "Correios"),
            Map.entry("JADLOG", "Jadlog"),
            Map.entry("LOGGI", "Loggi"),
            Map.entry("OTHER", "Outra"));

    private static final String HELP = """
            FindMyPacks CLI
            Usage: docker compose --profile cli run --rm cli <command> [options]

            Commands:
              help                         Show this help
              health                       Check API availability
              token <username> <password>  Obtain a Bearer token
              status                       Check the authenticated user
              create <trackingCode> <customer> <destination> <carrier> <status> <postedAt>
                                           Register a package
              update <id> <trackingCode> <customer> <destination> <carrier> <status> <postedAt>
                                           Edit a package
              list [page] [sortField] [direction]
                                           List five packages per page
              search [--tracking-code value] [--customer value] [--destination value]
                     [--carrier value] [--status value] [--page number] [--size number]
                     [--sort field] [--direction asc|desc]
                                           Search packages with combinable filters
              indicators                   Show the three dashboard indicators
              delete <id>                  Delete a package

            Environment:
              FINDMYPACKS_API_URL  API URL (default: http://app:8080)
              FINDMYPACKS_TOKEN    Bearer token used by protected commands
            """;

    public static void main(String[] args) {
        new CliApplication().run(args, System.getenv(), System.out, System.err, HttpClient.newHttpClient());
    }

    int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err, HttpClient client) {
        String command = args.length == 0 ? "help" : args[0];
        String baseUrl = environment.getOrDefault("FINDMYPACKS_API_URL", "http://app:8080");
        try {
            return switch (command) {
                case "help", "--help", "-h" -> printHelp(out);
                case "health" -> request(client, request(baseUrl + "/api/cli/health", "GET", null, null), out, err);
                case "token" -> token(args, baseUrl, client, out, err);
                case "status" -> protectedStatus(environment, baseUrl, client, out, err);
                case "create" -> writeRecord(args, 7, "POST", "/api/cli/records", environment,
                        baseUrl, client, out, err);
                case "update" -> writeRecord(args, 8, "PUT", "/api/cli/records/" + argument(args, 1), environment,
                        baseUrl, client, out, err);
                case "list" -> listRecords(args, environment, baseUrl, client, out, err);
                case "search" -> searchRecords(args, environment, baseUrl, client, out, err);
                case "indicators" -> dashboardIndicators(args, environment, baseUrl, client, out, err);
                case "delete" -> deleteRecord(args, environment, baseUrl, client, out, err);
                default -> unknown(command, err);
            };
        } catch (IOException exception) {
            err.println("Unable to connect to the API: " + exception.getMessage());
            return 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            err.println("API request interrupted");
            return 1;
        }
    }

    private int printHelp(PrintStream out) {
        out.print(HELP);
        return 0;
    }

    private int token(String[] args, String baseUrl, HttpClient client, PrintStream out, PrintStream err)
            throws IOException, InterruptedException {
        if (args.length != 3) {
            err.println("Usage: token <username> <password>");
            return 2;
        }
        String body = "{\"username\":\"" + json(args[1]) + "\",\"password\":\"" + json(args[2]) + "\"}";
        return request(client, request(baseUrl + "/api/cli/token", "POST", body, null), out, err);
    }

    private int protectedStatus(
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        String token = environment.get("FINDMYPACKS_TOKEN");
        if (token == null || token.isBlank()) {
            err.println("Set FINDMYPACKS_TOKEN before using protected commands");
            return 2;
        }
        return request(client, request(baseUrl + "/api/cli/status", "GET", null, token), out, err);
    }

    private int writeRecord(
            String[] args,
            int expectedArguments,
            String method,
            String path,
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        if (args.length != expectedArguments) {
            err.println(method.equals("POST")
                    ? "Usage: create <trackingCode> <customer> <destination> <carrier> <status> <postedAt>"
                    : "Usage: update <id> <trackingCode> <customer> <destination> <carrier> <status> <postedAt>");
            return 2;
        }
        String token = environment.get("FINDMYPACKS_TOKEN");
        if (token == null || token.isBlank()) {
            err.println("Set FINDMYPACKS_TOKEN before using protected commands");
            return 2;
        }
        int firstField = method.equals("POST") ? 1 : 2;
        String body = "{\"trackingCode\":\"" + json(args[firstField])
                + "\",\"customer\":\"" + json(args[firstField + 1])
                + "\",\"destination\":\"" + json(args[firstField + 2])
                + "\",\"carrier\":\"" + json(args[firstField + 3])
                + "\",\"status\":\"" + json(args[firstField + 4])
                + "\",\"postedAt\":\"" + json(args[firstField + 5]) + "\"}";
        return request(client, request(baseUrl + path, method, body, token), out, err);
    }

    private String argument(String[] args, int index) {
        return args.length > index ? args[index] : "";
    }

    private int listRecords(
            String[] args,
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        if (args.length > 4) {
            err.println("Usage: list [page] [sortField] [direction]");
            return 2;
        }
        String page = args.length > 1 ? args[1] : "0";
        String sortField = args.length > 2 ? args[2] : "postedAt";
        String direction = args.length > 3 ? args[3] : "desc";
        return protectedRequest(environment, baseUrl + "/api/cli/records?page=" + page
                + "&size=5&sort=" + sortField + "," + direction, "GET", client, out, err);
    }

    private int deleteRecord(
            String[] args,
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        if (args.length != 2) {
            err.println("Usage: delete <id>");
            return 2;
        }
        return protectedRequest(environment, baseUrl + "/api/cli/records/" + args[1],
                "DELETE", client, out, err);
    }

    private int dashboardIndicators(
            String[] args,
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        if (args.length != 1) {
            err.println("Usage: indicators");
            return 2;
        }
        String token = environment.get("FINDMYPACKS_TOKEN");
        if (token == null || token.isBlank()) {
            err.println("Set FINDMYPACKS_TOKEN before using protected commands");
            return 2;
        }
        HttpResponse<String> response = client.send(
                request(baseUrl + "/api/cli/dashboard", "GET", null, token),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            err.println("API returned HTTP " + response.statusCode() + ": " + response.body());
            return 1;
        }
        JsonNode summary;
        try {
            summary = JSON.readTree(response.body());
        } catch (JsonProcessingException exception) {
            err.println("API returned an invalid dashboard response");
            return 1;
        }
        if (!isDashboardSummary(summary)) {
            err.println("API returned an invalid dashboard response");
            return 1;
        }
        out.print(formatDashboardIndicators(summary));
        return 0;
    }

    private boolean isDashboardSummary(JsonNode summary) {
        if (summary == null || !summary.isObject()
                || !isCountList(summary.path("byStatus"))
                || !isCountList(summary.path("byCarrier"))) {
            return false;
        }
        JsonNode days = summary.path("postedPerDay");
        if (!days.isArray() || days.size() != 7) {
            return false;
        }
        for (JsonNode day : days) {
            JsonNode date = day.path("date");
            if (!date.isTextual() || !isNonNegativeLong(day.path("count"))) {
                return false;
            }
            try {
                LocalDate.parse(date.asText());
            } catch (DateTimeParseException exception) {
                return false;
            }
        }
        return true;
    }

    private boolean isCountList(JsonNode counts) {
        if (!counts.isArray()) {
            return false;
        }
        for (JsonNode item : counts) {
            if (!item.isObject() || !item.path("label").isTextual()
                    || !isNonNegativeLong(item.path("count"))) {
                return false;
            }
        }
        return true;
    }

    private boolean isNonNegativeLong(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0;
    }

    private String formatDashboardIndicators(JsonNode summary) {
        StringBuilder text = new StringBuilder("Indicadores do dashboard de pacotes\n");
        appendCounts(text, "Pacotes por status", summary.path("byStatus"), STATUSES);
        appendCounts(text, "Pacotes por transportadora", summary.path("byCarrier"), CARRIERS);
        text.append("Postagens nos últimos 7 dias:\n");
        for (JsonNode day : summary.path("postedPerDay")) {
            String date = LocalDate.parse(day.path("date").asText()).format(DISPLAY_DATE);
            text.append("  ").append(date).append(": ")
                    .append(day.path("count").asLong()).append('\n');
        }
        return text.toString();
    }

    private void appendCounts(
            StringBuilder text,
            String title,
            JsonNode counts,
            List<Map.Entry<String, String>> choices) {
        text.append(title).append(":\n");
        for (Map.Entry<String, String> choice : choices) {
            long count = 0;
            for (JsonNode item : counts) {
                if (choice.getKey().equals(item.path("label").asText())) {
                    count = item.path("count").asLong();
                    break;
                }
            }
            text.append("  ").append(choice.getValue()).append(": ").append(count).append('\n');
        }
    }

    private int searchRecords(
            String[] args,
            Map<String, String> environment,
            String baseUrl,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("page", "0");
        parameters.put("size", "5");
        parameters.put("sort", "postedAt,desc");

        Map<String, String> optionNames = Map.of(
                "--tracking-code", "trackingCode",
                "--customer", "customer",
                "--destination", "destination",
                "--carrier", "carrier",
                "--status", "status",
                "--page", "page",
                "--size", "size",
                "--sort", "sort",
                "--direction", "direction");
        if (args.length < 2 || (args.length - 1) % 2 != 0) {
            return searchUsage(err);
        }
        for (int index = 1; index < args.length; index += 2) {
            String parameter = optionNames.get(args[index]);
            if (parameter == null || args[index + 1].isBlank()) {
                return searchUsage(err);
            }
            parameters.put(parameter, args[index + 1]);
        }
        String direction = parameters.remove("direction");
        if (direction != null) {
            parameters.put("sort", parameters.get("sort").split(",", -1)[0] + "," + direction);
        }
        StringBuilder query = new StringBuilder();
        parameters.forEach((name, value) -> {
            if (query.length() > 0) query.append('&');
            query.append(encode(name)).append('=').append(encode(value));
        });
        return protectedRequest(environment, baseUrl + "/api/cli/records/search?" + query,
                "GET", client, out, err);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int searchUsage(PrintStream err) {
        err.println("Usage: search [--tracking-code value] [--customer value] [--destination value]"
                + " [--carrier value] [--status value] [--page number] [--size number]"
                + " [--sort field] [--direction asc|desc]");
        return 2;
    }

    private int protectedRequest(
            Map<String, String> environment,
            String url,
            String method,
            HttpClient client,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        String token = environment.get("FINDMYPACKS_TOKEN");
        if (token == null || token.isBlank()) {
            err.println("Set FINDMYPACKS_TOKEN before using protected commands");
            return 2;
        }
        return request(client, request(url, method, null, token), out, err);
    }

    private HttpRequest request(String url, String method, String body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return request.build();
    }

    private int request(
            HttpClient client,
            HttpRequest request,
            PrintStream out,
            PrintStream err) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            out.println(response.body());
            return 0;
        }
        err.println("API returned HTTP " + response.statusCode() + ": " + response.body());
        return 1;
    }

    private int unknown(String command, PrintStream err) {
        err.println("Unknown command: " + command + ". Run 'help' to list commands.");
        return 2;
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
