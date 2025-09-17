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

    @Option(names = {"--port"}, description = "Port to listen on")
    private int port = 8080;

    @Option(names = {"--auth-token"}, description = "Optional bearer token for simple auth")
    private String authToken = null;

    public void run() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
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
        } catch (Exception ex) {
            System.err.println("Failed to start server: " + ex.getMessage());
            System.exit(2);
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
                String query = exchange.getRequestURI().getQuery();
                long fromLsn = 0L;
                if (query != null && query.contains("from_lsn=")) {
                    String[] parts = query.split("from_lsn=");
                    try { fromLsn = Long.parseLong(parts[1].split("&")[0]); } catch (Exception e) { }
                }
                // call export to temp file
                File tmp = Files.createTempFile("export", ".jsonl").toFile();
                // using local DB path 'data/app.db' by default
                String dbPath = "data/app.db";
                ExportChangeLogCommand.run(dbPath, fromLsn, tmp.getAbsolutePath());
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
                // read body to temp file
                File tmp = Files.createTempFile("import", ".jsonl").toFile();
                try (InputStream is = exchange.getRequestBody(); FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192]; int r;
                    while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                }
                String dbPath = "data/app.db";
                ImportChangeLogCommand.run(dbPath, tmp.getAbsolutePath());
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
