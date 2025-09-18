package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class SyncStateRepositoryUpsertTest {
    private Path dbPath;
    private SqliteConnectionManager cm;
    private SyncStateRepository repo;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = Path.of("data", "test_syncstate.db");
        Files.deleteIfExists(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))");
        }
        repo = new SyncStateRepository(cm);
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
    }

    @Test
    public void upsert_updatesOnlyWhenGreater() throws Exception {
        repo.upsertLastLsn("clientX", 100L);
        Long v1 = repo.getLastLsn("clientX");
        assertThat(v1).isEqualTo(100L);

        // attempt to downsert a smaller value
        repo.upsertLastLsn("clientX", 90L);
        Long v2 = repo.getLastLsn("clientX");
        assertThat(v2).isEqualTo(100L);

        // upsert larger value
        repo.upsertLastLsn("clientX", 200L);
        Long v3 = repo.getLastLsn("clientX");
        assertThat(v3).isEqualTo(200L);
    }
}
