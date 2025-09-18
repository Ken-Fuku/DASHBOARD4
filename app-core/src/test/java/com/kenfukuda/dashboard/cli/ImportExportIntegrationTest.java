package com.kenfukuda.dashboard.cli;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository;
import com.kenfukuda.dashboard.infrastructure.repository.SyncStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class ImportExportIntegrationTest {
    private Path dbPath;
    private SqliteConnectionManager cm;
    private ChangeLogRepository repo;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = Path.of("data", "test_import_export.db");
        Files.deleteIfExists(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT) ");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))");
        }
        repo = new ChangeLogRepository(cm);
        // insert sample entries in source (simulate remote)
        repo.insert(new com.kenfukuda.dashboard.domain.model.ChangeLogEntry(0, "tx1", "company", "{\"id\":1}", "I", "{\"id\":1}", 0, null));
        repo.insert(new com.kenfukuda.dashboard.domain.model.ChangeLogEntry(0, "tx2", "store", "{\"id\":2}", "I", "{\"id\":2}", 0, null));
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
    }

    @Test
    public void import_isIdempotent_and_updatesSyncState() throws Exception {
        // Export from dbPath (acts as source)
        File out = Files.createTempFile("changelog-export-integ-", ".jsonl").toFile();
        out.deleteOnExit();
        ExportChangeLogCommand.run(dbPath.toString(), 0L, out.getAbsolutePath());

        // Create target DB for import (different file)
        Path targetDb = Path.of("data", "test_import_target.db");
        Files.deleteIfExists(targetDb);
        SqliteConnectionManager targetCm = new SqliteConnectionManager(targetDb.toString());
        try (Connection c = targetCm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
            s.execute("CREATE TABLE sync_state (client_id TEXT PRIMARY KEY, last_lsn INTEGER, updated_at TEXT DEFAULT (datetime('now')))");
        }

        // First import
        ImportChangeLogCommand.run(targetDb.toString(), out.getAbsolutePath(), "client-A");

        // Verify sync_state updated
        SyncStateRepository ssr = new SyncStateRepository(targetCm);
        Long lastLsn = ssr.getLastLsn("client-A");
        assertThat(lastLsn).isNotNull();
        assertThat(lastLsn).isGreaterThan(0L);

        // Re-run import (idempotent): should not duplicate entries
        ImportChangeLogCommand.run(targetDb.toString(), out.getAbsolutePath(), "client-A");

        // Verify no duplicate tx_id entries (count should equal number of unique tx ids = 2)
        try (Connection c = targetCm.getConnection(); Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM change_log")) {
            if (rs.next()) {
                int cnt = rs.getInt(1);
                assertThat(cnt).isEqualTo(2);
            }
        }

        // cleanup target db
        try { Files.deleteIfExists(targetDb); } catch (Exception ignored) {}
    }
}
