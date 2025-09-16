package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

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
            s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')))");
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
}
