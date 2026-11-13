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
              create <trackingCode> <customer> <destination> <carrier> <status> <postedAt>
                                           Register a package
              update <id> <trackingCode> <customer> <destination> <carrier> <status> <postedAt>
                                           Edit a package
              list [page] [sortField] [direction]
                                           List five packages per page
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
