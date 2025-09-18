package com.kenfukuda.dashboard.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;

public class ServeHttpIntegrationTest {
    static Thread serverThread;

    @BeforeAll
    public static void startServer() throws Exception {
        // prepare test DB
        java.nio.file.Path p = java.nio.file.Path.of("data", "test_server.db");
        java.nio.file.Files.createDirectories(p.getParent());
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + p.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')))");
            }
        }

        serverThread = new Thread(() -> {
            RunCli.main(new String[]{"serve", "--port", "9090", "--auth-token", "test-token", "--db", p.toString()});
        });
        serverThread.setDaemon(false);
        serverThread.start();
        // give server time to start
        Thread.sleep(1500);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        ServeCommand.stopServer();
        try { serverThread.join(1000); } catch (Exception e) {}
    }

    @Test
    public void testUnauthorizedPull() throws Exception {
        URL url = new URL("http://localhost:9090/sync/pull?from_lsn=0");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        int code = con.getResponseCode();
        assertEquals(401, code);
    }

    @Test
    public void testAuthorizedPull() throws Exception {
        URL url = new URL("http://localhost:9090/sync/pull?from_lsn=0");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer test-token");
        int code = con.getResponseCode();
        assertEquals(200, code);
        try (InputStream is = con.getInputStream()) {
            byte[] b = is.readNBytes(2);
            // response might be empty or a meta line; just ensure stream reads (no exception)
            assertNotNull(b);
        }
    }
}
