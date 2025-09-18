package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class ChangeLogRepositoryTest {
    private SqliteConnectionManager cm;
    private ChangeLogRepository repo;

    @BeforeEach
    public void setUp() throws Exception {
        // use a temp DB file for tests
        Path p = Path.of("data", "test_app.db");
        if (Files.exists(p)) Files.delete(p);
        cm = new SqliteConnectionManager(p.toString());
        // create minimal schema
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))" );
        }
        repo = new ChangeLogRepository(cm);
    }

    @AfterEach
    public void tearDown() throws Exception {
        // delete test DB
        Path p = Path.of("data", "test_app.db");
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    @Test
    public void testInsertAndList() throws Exception {
        ChangeLogEntry e = new ChangeLogEntry(0, "tx1", "company", "{\"id\":1}", "I", "{\"id\":1}", 0, null);
        long lsn = repo.insert(e);
        assertTrue(lsn > 0);

        long max = repo.getMaxLsn();
        assertEquals(lsn, max);

        List<ChangeLogEntry> list = repo.listFrom(0);
        assertFalse(list.isEmpty());
        ChangeLogEntry got = list.get(0);
        assertEquals("company", got.getTableName());
        assertEquals("I", got.getOp());
    }

    @Test
    public void testApplyCompositePkAndTypeConversion() throws Exception {
        // create target table with composite PK and types
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE product (id INTEGER, sku TEXT, qty INTEGER, PRIMARY KEY(id, sku))");
        }
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx2", "product", "{\"id\":1, \"sku\":\"A1\"}", "I", "{\"id\":1, \"sku\":\"A1\", \"qty\": 10}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            // update using string for qty (type conversion)
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx3", "product", "{\"id\":1, \"sku\":\"A1\"}", "U", "{\"qty\": \"15\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT qty FROM product WHERE id=1 AND sku='A1'")) {
                assertTrue(rs.next());
                assertEquals(15, rs.getInt(1));
            }
            // delete
            ChangeLogEntry e3 = new ChangeLogEntry(0, "tx4", "product", "{\"id\":1, \"sku\":\"A1\"}", "D", null, 1, null);
            repo.applyEntry(c, e3);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM product WHERE id=1 AND sku='A1'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    public void testApplyWithNullPkAndTypeEdgeCases() throws Exception {
        // table with composite PK where one column can be NULL
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE widget (id INTEGER, variant TEXT, val REAL, PRIMARY KEY(id, variant))");
        }
        // insert with variant = NULL
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx5", "widget", "{\"id\":1, \"variant\":null}", "I", "{\"id\":1, \"variant\":null, \"val\": 1.5}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT val FROM widget WHERE id=1 AND variant IS NULL")) {
                assertTrue(rs.next());
                assertEquals(1.5, rs.getDouble(1), 0.0001);
            }

            // update using string value for val
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx6", "widget", "{\"id\":1, \"variant\":null}", "U", "{\"val\": \"2\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT val FROM widget WHERE id=1 AND variant IS NULL")) {
                assertTrue(rs.next());
                assertEquals(2.0, rs.getDouble(1), 0.0001);
            }

            // delete using null pk
            ChangeLogEntry e3 = new ChangeLogEntry(0, "tx7", "widget", "{\"id\":1, \"variant\":null}", "D", null, 1, null);
            repo.applyEntry(c, e3);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM widget WHERE id=1 AND variant IS NULL")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    public void testPkKeyOrderIndependence() throws Exception {
        // table with composite PK (id, sku) but we will provide pkJson with keys reversed
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE product2 (id INTEGER, sku TEXT, qty INTEGER, PRIMARY KEY(id, sku))");
        }
        // pkJson with reversed key order
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx10", "product2", "{\"sku\":\"B2\", \"id\":2}", "I", "{\"id\":2, \"sku\":\"B2\", \"qty\": 5}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            // update using another order
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx11", "product2", "{\"id\":2, \"sku\":\"B2\"}", "U", "{\"qty\": \"8\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT qty FROM product2 WHERE id=2 AND sku='B2'")) {
                assertTrue(rs.next());
                assertEquals(8, rs.getInt(1));
            }
        }
    }

    @Test
    public void testBooleanAndDateStringHandling() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE event (id INTEGER PRIMARY KEY, active INTEGER, occurred_at TEXT)");
        }
        ChangeLogEntry ei = new ChangeLogEntry(0, "tx20", "event", "{\"id\":1}", "I", "{\"id\":1, \"active\": 0, \"occurred_at\": \"2025-01-01T00:00:00Z\"}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, ei);
            // update boolean via string
            ChangeLogEntry eu = new ChangeLogEntry(0, "tx21", "event", "{\"id\":1}", "U", "{\"active\": \"true\"}", 0, null);
            repo.applyEntry(c, eu);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT active FROM event WHERE id=1")) {
                assertTrue(rs.next());
                // active stored as 1 (true)
                assertEquals(1, rs.getInt(1));
            }
            // update occurred_at with another date string
            ChangeLogEntry ed = new ChangeLogEntry(0, "tx22", "event", "{\"id\":1}", "U", "{\"occurred_at\": \"2025-09-01T12:34:56Z\"}", 0, null);
            repo.applyEntry(c, ed);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT occurred_at FROM event WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals("2025-09-01T12:34:56Z", rs.getString(1));
            }
        }
    }

    @Test
    public void testBlobBase64Handling() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE doc (id INTEGER PRIMARY KEY, data BLOB, note TEXT)");
        }
        // create some bytes and base64-encode them in payload
        byte[] raw = new byte[] {1, 2, 3, 4, 5};
        String b64 = Base64.getEncoder().encodeToString(raw);
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx30", "doc", "{\"id\":1}", "I", "{\"id\":1, \"data\": \"" + b64 + "\", \"note\": \"blob1\"}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT data, note FROM doc WHERE id=1")) {
                assertTrue(rs.next());
                byte[] got = rs.getBytes(1);
                assertArrayEquals(raw, got);
                assertEquals("blob1", rs.getString(2));
            }

            // update with raw bytes encoded as base64 again
            byte[] raw2 = new byte[] {9,8,7};
            String b64_2 = Base64.getEncoder().encodeToString(raw2);
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx31", "doc", "{\"id\":1}", "U", "{\"data\": \"" + b64_2 + "\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT data FROM doc WHERE id=1")) {
                assertTrue(rs.next());
                assertArrayEquals(raw2, rs.getBytes(1));
            }
        }
    }

    @Test
    public void testDateAndBooleanVariantsStorage() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE evt2 (id INTEGER PRIMARY KEY, flag INTEGER, ts TEXT)");
        }
        // insert with numeric boolean and ISO date without Z
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx40", "evt2", "{\"id\":1}", "I", "{\"id\":1, \"flag\": 1, \"ts\": \"2025-09-18 15:00:00\"}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            // update flag using string 'false' and ts with offset
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx41", "evt2", "{\"id\":1}", "U", "{\"flag\": \"false\", \"ts\": \"2025-09-18T15:00:00+09:00\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT flag, ts FROM evt2 WHERE id=1")) {
                assertTrue(rs.next());
                // flag should be stored as 0 (false)
                assertEquals(0, rs.getInt(1));
                assertEquals("2025-09-18T15:00:00+09:00", rs.getString(2));
            }
        }
    }

    @Test
    public void testBlobBase64PaddingAndInvalidFallback() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE doc2 (id INTEGER PRIMARY KEY, data BLOB, note TEXT)");
        }
        byte[] raw = new byte[] {10,20,30};
        String b64 = Base64.getEncoder().encodeToString(raw); // normal padded base64
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx50", "doc2", "{\"id\":1}", "I", "{\"id\":1, \"data\": \"" + b64 + "\", \"note\": \"okpad\"}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT data, note FROM doc2 WHERE id=1")) {
                assertTrue(rs.next());
                assertArrayEquals(raw, rs.getBytes(1));
                assertEquals("okpad", rs.getString(2));
            }

            // create a 'base64-like' string missing padding (invalid) - decoder may throw and fallback to raw bytes
            String trimmed = b64.replace("=", "");
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx51", "doc2", "{\"id\":1}", "U", "{\"data\": \"" + trimmed + "\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT data FROM doc2 WHERE id=1")) {
                assertTrue(rs.next());
                byte[] actual = rs.getBytes(1);
                // when padding removed: decoder may either successfully decode to original raw bytes, or fail and repository stores raw string bytes
                if (java.util.Arrays.equals(actual, raw)) {
                    // decoded back to original bytes - acceptable
                } else {
                    assertArrayEquals(trimmed.getBytes(), actual);
                }
            }

            // update with a clearly invalid base64 string - expect raw bytes stored
            String notb64 = "not-base64!!";
            ChangeLogEntry e3 = new ChangeLogEntry(0, "tx52", "doc2", "{\"id\":1}", "U", "{\"data\": \"" + notb64 + "\"}", 0, null);
            repo.applyEntry(c, e3);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT data FROM doc2 WHERE id=1")) {
                assertTrue(rs.next());
                assertArrayEquals(notb64.getBytes(), rs.getBytes(1));
            }
        }
    }

    @Test
    public void testDateColumnsDeclaredTypes() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE tdates (id INTEGER PRIMARY KEY, d DATE, dt TIMESTAMP, ttext TEXT)");
        }
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx60", "tdates", "{\"id\":1}", "I", "{\"id\":1, \"d\": \"2025-09-18\", \"dt\": \"2025-09-18T16:00:00Z\", \"ttext\": \"plain\"}", 0, null);
        try (Connection c = cm.getConnection()) {
            repo.applyEntry(c, e1);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT d, dt, ttext FROM tdates WHERE id=1")) {
                assertTrue(rs.next());
                assertEquals("2025-09-18", rs.getString(1));
                assertEquals("2025-09-18T16:00:00Z", rs.getString(2));
                assertEquals("plain", rs.getString(3));
            }

            // update dt with epoch-like numeric string - repository will try to parse numeric and store as number
            ChangeLogEntry e2 = new ChangeLogEntry(0, "tx61", "tdates", "{\"id\":1}", "U", "{\"dt\": \"1695043200\"}", 0, null);
            repo.applyEntry(c, e2);
            try (ResultSet rs = c.createStatement().executeQuery("SELECT dt FROM tdates WHERE id=1")) {
                assertTrue(rs.next());
                // numeric stored; retrieving as string yields numeric string
                assertEquals("1695043200", rs.getString(1));
            }
        }
    }
}
