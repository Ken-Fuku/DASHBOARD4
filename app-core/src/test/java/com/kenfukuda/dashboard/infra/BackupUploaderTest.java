package com.kenfukuda.dashboard.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackupUploaderTest {

    @Test
    public void uploadToLocalHttpServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        Path tmpDir = Files.createTempDirectory("uploader-test");
        try {
            Path received = tmpDir.resolve("received.bin");
            server.createContext("/upload", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try (InputStream in = exchange.getRequestBody(); OutputStream out = Files.newOutputStream(received)) {
                        in.transferTo(out);
                        exchange.sendResponseHeaders(200, -1);
                    } catch (Exception e) {
                        try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignore) {}
                    } finally {
                        exchange.close();
                    }
                }
            });
            server.start();
            int port = server.getAddress().getPort();
            Path file = tmpDir.resolve("toSend.bin");
            Files.write(file, "hello".getBytes());

            BackupUploader u = new BackupUploader();
            URL url = new URL("http://127.0.0.1:" + port + "/upload");
            u.upload(file, url);

            assertTrue(Files.exists(received));
            assertEquals("hello", Files.readString(received));
        } finally {
            server.stop(0);
            try { Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} }); } catch (Exception ignore) {}
        }
    }

    @Test
    public void uploadWithHeaders() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        Path tmpDir = Files.createTempDirectory("uploader-test-headers");
        try {
            Path received = tmpDir.resolve("received2.bin");
            final String[] gotHeader = new String[1];
            server.createContext("/upload2", exchange -> {
                try (InputStream in = exchange.getRequestBody(); OutputStream out = Files.newOutputStream(received)) {
                    gotHeader[0] = exchange.getRequestHeaders().getFirst("X-Custom-Header");
                    in.transferTo(out);
                    exchange.sendResponseHeaders(200, -1);
                } catch (Exception e) {
                    try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignore) {}
                } finally { exchange.close(); }
            });
            server.start();
            int port = server.getAddress().getPort();
            Path file = tmpDir.resolve("toSend2.bin");
            Files.write(file, "hello2".getBytes());

            BackupUploader u = new BackupUploader(2);
            URL url = new URL("http://127.0.0.1:" + port + "/upload2");
            u.upload(file, url, java.util.Map.of("X-Custom-Header", "value-123"));

            assertTrue(Files.exists(received));
            assertEquals("hello2", Files.readString(received));
            assertEquals("value-123", gotHeader[0]);
        } finally {
            server.stop(0);
            try { Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} }); } catch (Exception ignore) {}
        }
    }
}
