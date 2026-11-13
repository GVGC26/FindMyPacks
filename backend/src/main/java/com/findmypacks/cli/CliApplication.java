package com.findmypacks.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class CliApplication {
    private static final String HELP = """
            FindMyPacks CLI
            Usage: docker compose --profile cli run --rm cli <command> [options]

            Commands:
              help                         Show this help
              health                       Check API availability
              token <username> <password>  Obtain a Bearer token
              status                       Check the authenticated user

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
                case "health" -> request(client, request(baseUrl + "/api/cli/health", null, null), out, err);
                case "token" -> token(args, baseUrl, client, out, err);
                case "status" -> protectedStatus(environment, baseUrl, client, out, err);
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
        return request(client, request(baseUrl + "/api/cli/token", body, null), out, err);
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
        return request(client, request(baseUrl + "/api/cli/status", null, token), out, err);
    }

    private HttpRequest request(String url, String body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
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
