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

public class IntegrationRepositoryTest {
    private SqliteConnectionManager cm;
    private ChangeLogRepository clogRepo;
    private SyncStateRepository syncRepo;

    @BeforeEach
    public void setUp() throws Exception {
        Path p = Path.of("data", "test_app.db");
        if (Files.exists(p)) Files.delete(p);
        cm = new SqliteConnectionManager(p.toString());
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))");
        }
        clogRepo = new ChangeLogRepository(cm);
        syncRepo = new SyncStateRepository(cm);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Path p = Path.of("data", "test_app.db");
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }

    @Test
    public void testChangeLogAndSyncStateTogether() throws Exception {
    // insert change log entries using ChangeLogEntry (matches repository API)
    ChangeLogEntry e1 = new ChangeLogEntry(0, "tx1", "company", "{\"id\":1}", "INSERT", "{\"name\":\"A\"}", 0, null);
    ChangeLogEntry e2 = new ChangeLogEntry(0, "tx2", "company", "{\"id\":2}", "INSERT", "{\"name\":\"B\"}", 0, null);

    long lsn1 = clogRepo.insert(e1);
    long lsn2 = clogRepo.insert(e2);

    assertTrue(lsn2 > lsn1);

    List<ChangeLogEntry> list = clogRepo.listFrom(lsn1);
        assertNotNull(list);
        assertTrue(list.size() >= 1);

        // store last lsn into sync_state for a client
        syncRepo.upsertLastLsn("clientX", lsn2);
        Long stored = syncRepo.getLastLsn("clientX");
        assertNotNull(stored);
        assertEquals(lsn2, stored.longValue());

        // simulate incremental fetch
    List<ChangeLogEntry> later = clogRepo.listFrom(lsn1 + 1);
        assertTrue(later.stream().allMatch(e -> e.getLsn() > lsn1));
    }
}
