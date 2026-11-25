package com.example.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

// Minimal HTTP server standing in for a downstream service; records every request it receives
class StubServer {

    final List<String> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    StubServer(Function<HttpExchange, String> jsonBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + " source=" + exchange.getRequestHeaders().getFirst("X-Request-Source"));
            byte[] body = jsonBody.apply(exchange).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void stop() {
        server.stop(0);
    }

    // A port with nothing listening on it: calls to it fail like a stopped service
    static String deadUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        }
    }

    static String product(HttpExchange exchange, String instance) {
        String path = exchange.getRequestURI().getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        return "{\"id\":" + id + ",\"name\":\"Laptop\",\"price\":75000,\"instance\":\"" + instance + "\"}";
    }
}
