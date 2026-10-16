package ai.jgp.hackathon.snowflake.selfreport;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A stand-in for the GitHub REST API on a loopback port, so no test ever calls the real one.
 * Records every request body; {@link #issueStatus} switches the issue endpoint to a failure.
 */
final class StubGitHub implements AutoCloseable {

    static final String ISSUE_URL = "https://github.com/jgperrin/ai.jgp.hackathon.snowflake/issues/4242";

    private final HttpServer server;
    final List<String> requests = new CopyOnWriteArrayList<>();
    volatile int issueStatus = 201;

    StubGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String apiBase() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Every recorded request, method, path and body, as one string to search. */
    String all() {
        return String.join("\n", requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        requests.add(method + " " + path + "\n" + body);
        if ("POST".equals(method) && path.endsWith("/issues")) {
            respond(exchange, issueStatus, issueStatus == 201 ? "{\"html_url\":\"" + ISSUE_URL + "\"}"
                    : "{\"message\":\"Validation Failed\"}");
        } else if ("PUT".equals(method) && path.contains("/contents/")) {
            respond(exchange, 201, "{}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
