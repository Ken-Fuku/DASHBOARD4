package com.kenfukuda.dashboard.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.File;
import java.nio.file.Files;
import com.kenfukuda.dashboard.cli.RunCli;

public class PushPullConflictE2ETest {
    static Thread serverThread;
    static java.nio.file.Path mainDb;
    static java.nio.file.Path clientDb;

    @BeforeAll
    public static void startMainServer() throws Exception {
        mainDb = java.nio.file.Path.of("data", "e2e_main.db");
        clientDb = java.nio.file.Path.of("data", "e2e_client.db");
        Files.createDirectories(mainDb.getParent());
        Files.deleteIfExists(mainDb);
        Files.deleteIfExists(clientDb);
        // prepare main DB with a company row and triggers
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + mainDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE IF NOT EXISTS company (id INTEGER PRIMARY KEY AUTOINCREMENT, company_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
                s.execute("INSERT INTO company (company_code, name) VALUES ('C001', 'Main Co')");
            }
        }

        serverThread = new Thread(() -> {
            RunCli.main(new String[]{"serve", "--port", "9191", "--auth-token", "e2e-token", "--db", mainDb.toString(), "--node-id", "main-node", "--main-node-id", "main-node"});
        });
        serverThread.setDaemon(false);
        serverThread.start();
        Thread.sleep(1200);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        com.kenfukuda.dashboard.cli.ServeCommand.stopServer();
        try { serverThread.join(1000); } catch (Exception e) {}
        try { Files.deleteIfExists(mainDb); } catch (Exception ignore) {}
        try { Files.deleteIfExists(clientDb); } catch (Exception ignore) {}
    }

    @Test
    public void clientPushShouldNotOverwriteMainForPreferTables() throws Exception {
        // prepare client DB with same PK but different name
        Files.deleteIfExists(clientDb);
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + clientDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE IF NOT EXISTS company (id INTEGER PRIMARY KEY AUTOINCREMENT, company_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
                // client inserts company with same code but different name
                s.execute("INSERT INTO company (company_code, name) VALUES ('C001', 'Client Co')");
            }
        }

        // export client's change_log (fromLsn=0) to temp file
        File tmp = Files.createTempFile("e2e-client-export", ".jsonl").toFile();
        com.kenfukuda.dashboard.cli.ExportChangeLogCommand.run(clientDb.toString(), 0L, tmp.getAbsolutePath(), "client-node");

        // POST the file to main server /sync/push
        URL url = new URL("http://localhost:9191/sync/push");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "Bearer e2e-token");
        byte[] body = Files.readAllBytes(tmp.toPath());
        con.setFixedLengthStreamingMode(body.length);
        con.getOutputStream().write(body);
        int code = con.getResponseCode();
        assertEquals(200, code);

        // check main DB: company name should remain 'Main Co' because company is prefer-main
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + mainDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT name FROM company WHERE company_code='C001'")) {
                assertTrue(rs.next());
                String name = rs.getString(1);
                assertEquals("Main Co", name);
            }
        }
    }

    @Test
    public void lwwShouldLetClientWinForNonPreferTable() throws Exception {
        // prepare main DB with a department row and an older changelog entry
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + mainDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS department (id INTEGER PRIMARY KEY AUTOINCREMENT, dept_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
                s.execute("INSERT INTO department (dept_code, name) VALUES ('D001', 'Main Dept')");
                s.execute("INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node) VALUES ('t-main-dept', 'department', '{\"id\":1}', 'I', '{\"id\":1,\"name\":\"Main Dept\"}', 0, '2020-01-01T00:00:00Z', 'main-node')");
            }
        }

    // prepare client DB with a newer department change in change_log
    Files.deleteIfExists(clientDb);
    try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + clientDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE IF NOT EXISTS department (id INTEGER PRIMARY KEY AUTOINCREMENT, dept_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
                // client has a newer change for the same PK
                s.execute("INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node) VALUES ('t-client-dept', 'department', '{\"id\":1}', 'U', '{\"id\":1,\"name\":\"Client Dept New\"}', 0, '2020-01-02T00:00:00Z', 'client-node')");
            }
        }

        // export client's change_log (fromLsn=0) to temp file and push to main
        File tmp = Files.createTempFile("e2e-client-dept-export", ".jsonl").toFile();
        com.kenfukuda.dashboard.cli.ExportChangeLogCommand.run(clientDb.toString(), 0L, tmp.getAbsolutePath(), "client-node");

        URL url = new URL("http://localhost:9191/sync/push");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "Bearer e2e-token");
        byte[] body = Files.readAllBytes(tmp.toPath());
        con.setFixedLengthStreamingMode(body.length);
        con.getOutputStream().write(body);
        int code = con.getResponseCode();
        assertEquals(200, code);

        // check main DB: department name should be updated to client's newer value
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + mainDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT payload FROM change_log WHERE table_name='department' ORDER BY lsn DESC LIMIT 1")) {
                assertTrue(rs.next());
                String payload = rs.getString(1);
                assertTrue(payload.contains("Client Dept New"));
            }
        }
    }

    @Test
    public void pullShouldReturnMainChangesAndClientImportsThem() throws Exception {
        // ensure main has a store row with a changelog entry
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + mainDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS store (id INTEGER PRIMARY KEY AUTOINCREMENT, store_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
                s.execute("INSERT INTO store (store_code, name) VALUES ('S001', 'Main Store')");
                s.execute("INSERT INTO change_log (tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node) VALUES ('t-main-store', 'store', '{\"id\":1}', 'I', '{\"id\":1,\"name\":\"Main Store\"}', 0, '2021-01-01T00:00:00Z', 'main-node')");
            }
        }

    // client DB starts empty
    Files.deleteIfExists(clientDb);
    try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + clientDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE IF NOT EXISTS store (id INTEGER PRIMARY KEY AUTOINCREMENT, store_code TEXT UNIQUE, name TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
            }
        }

        // pull from main server
        URL url = new URL("http://localhost:9191/sync/pull?from_lsn=0");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer e2e-token");
        int code = con.getResponseCode();
        assertEquals(200, code);
        File tmp = Files.createTempFile("e2e-pull", ".jsonl").toFile();
        try (InputStream is = con.getInputStream(); java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
            byte[] buf = new byte[8192]; int r;
            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
        }

        // import into client using mainNodeId so incomingFromMain=true
        com.kenfukuda.dashboard.cli.ImportChangeLogCommand.runWithMainNodeId(clientDb.toString(), tmp.getAbsolutePath(), "main-node");

        // verify client received the changelog entry for store
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + clientDb.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT payload FROM change_log WHERE table_name='store' ORDER BY lsn DESC LIMIT 1")) {
                assertTrue(rs.next());
                String payload = rs.getString(1);
                assertTrue(payload.contains("Main Store"));
            }
        }
    }
}
