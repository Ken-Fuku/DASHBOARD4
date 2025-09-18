package com.kenfukuda.dashboard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

@Command(name = "serve", description = "Start embedded HTTP server for /sync endpoints")
public class ServeCommand implements Runnable {

    private static HttpServer serverRef = null;

    @Option(names = {"--port"}, description = "Port to listen on")
    private int port = 8080;

    @Option(names = {"--auth-token"}, description = "Optional bearer token for simple auth")
    private String authToken = null;

    @Option(names = {"--db"}, description = "Path to SQLite DB file")
    private String dbPath = "data/app.db";

    @Option(names = {"--node-id"}, description = "Node identifier used as source_node in exports")
    private String nodeId = "local";

    @Option(names = {"--main-node-id"}, description = "Identifier of the main node to consider for conflict resolution")
    private String mainNodeId = null;

    public void run() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            serverRef = server;
            server.createContext("/sync/pull", new PullHandler());
            server.createContext("/sync/push", new PushHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            System.out.println("Starting HTTP server on port " + port);
            server.start();
            // keep running until interrupted
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping HTTP server");
                server.stop(1);
            }));
            // block current thread to keep server running when invoked programmatically
            synchronized (this) {
                this.wait();
            }
        } catch (Exception ex) {
            System.err.println("Failed to start server: " + ex.getMessage());
            System.exit(2);
        }
    }

    public static void stopServer() {
        try {
            if (serverRef != null) {
                serverRef.stop(0);
                serverRef = null;
            }
        } catch (Exception ex) {
            System.err.println("Error stopping server: " + ex.getMessage());
        }
    }

    class PullHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                // auth check
                if (authToken != null && !authToken.isEmpty()) {
                    String auth = exchange.getRequestHeaders().getFirst("Authorization");
                    if (auth == null || !auth.equals("Bearer " + authToken)) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                }
                String query = exchange.getRequestURI().getQuery();
                long fromLsn = 0L;
                if (query != null && query.contains("from_lsn=")) {
                    String[] parts = query.split("from_lsn=");
                    try { fromLsn = Long.parseLong(parts[1].split("&")[0]); } catch (Exception e) { }
                }
                // call export to temp file (include server node id as source_node)
                File tmp = Files.createTempFile("export", ".jsonl").toFile();
                ExportChangeLogCommand.run(dbPath, fromLsn, tmp.getAbsolutePath(), nodeId);
                byte[] body = Files.readAllBytes(tmp.toPath());
                exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
                tmp.delete();
            } catch (Exception ex) {
                try { exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); } catch (Exception e) {}
                System.err.println("PullHandler error: " + ex.getMessage());
            }
        }
    }

    class PushHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"POST".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                // auth check
                if (authToken != null && !authToken.isEmpty()) {
                    String auth = exchange.getRequestHeaders().getFirst("Authorization");
                    if (auth == null || !auth.equals("Bearer " + authToken)) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                }
                // read body to temp file
                File tmp = Files.createTempFile("import", ".jsonl").toFile();
                try (InputStream is = exchange.getRequestBody(); FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192]; int r;
                    while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                }
                // pass mainNodeId to import so it can detect if incoming changes are from main
                ImportChangeLogCommand.runWithMainNodeId(dbPath, tmp.getAbsolutePath(), mainNodeId);
                String resp = "ok";
                byte[] out = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(200, out.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(out); }
                tmp.delete();
            } catch (Exception ex) {
                try { exchange.sendResponseHeaders(500, 0); exchange.getResponseBody().close(); } catch (Exception e) {}
                System.err.println("PushHandler error: " + ex.getMessage());
            }
        }
    }
}
