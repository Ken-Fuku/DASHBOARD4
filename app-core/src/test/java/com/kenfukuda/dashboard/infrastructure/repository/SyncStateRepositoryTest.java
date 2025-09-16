package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class SyncStateRepositoryTest {
    private SqliteConnectionManager cm;
    private SyncStateRepository repo;

    @BeforeEach
    public void setUp() throws Exception {
        Path p = Path.of("data", "test_app.db");
        if (Files.exists(p)) Files.delete(p);
        cm = new SqliteConnectionManager(p.toString());
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))");
        }
        repo = new SyncStateRepository(cm);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Path p = Path.of("data", "test_app.db");
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    @Test
    public void testUpsertAndGet() throws Exception {
        repo.upsertLastLsn("clientA", 100L);
        Long v = repo.getLastLsn("clientA");
        assertNotNull(v);
        assertEquals(100L, v);

        repo.upsertLastLsn("clientA", 200L);
        Long v2 = repo.getLastLsn("clientA");
        assertEquals(200L, v2);
    }
}
