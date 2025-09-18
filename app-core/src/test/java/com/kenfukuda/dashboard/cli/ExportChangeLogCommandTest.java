package com.kenfukuda.dashboard.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExportChangeLogCommandTest {
    private Path dbPath;
    private SqliteConnectionManager cm;
    private ChangeLogRepository repo;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = Path.of("data", "test_export.db");
        Files.deleteIfExists(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE change_log (lsn INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, table_name TEXT, pk_json TEXT, op TEXT, payload TEXT, tombstone INTEGER DEFAULT 0, created_at TEXT DEFAULT (datetime('now')), source_node TEXT)");
        }
        repo = new ChangeLogRepository(cm);
        // insert sample entries
        ChangeLogEntry e1 = new ChangeLogEntry(0, "tx1", "company", "{\"id\":1}", "I", "{\"id\":1}", 0, null);
        ChangeLogEntry e2 = new ChangeLogEntry(0, "tx2", "store", "{\"id\":2}", "U", "{\"name\":\"S2\"}", 0, null);
        repo.insert(e1);
        repo.insert(e2);
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
    }

    @Test
    public void export_writesMetaAndEntries_asJsonLines() throws Exception {
        File out = Files.createTempFile("changelog-export-test-", ".jsonl").toFile();
        out.deleteOnExit();

        // Act: call the command's programmatic API
        ExportChangeLogCommand.run(dbPath.toString(), 0L, out.getAbsolutePath());

        // Assert
        List<String> lines = Files.readAllLines(out.toPath());
        assertThat(lines).isNotEmpty();
        ObjectMapper om = new ObjectMapper();
        JsonNode metaWrap = om.readTree(lines.get(0));
        assertThat(metaWrap.has("__meta__")).isTrue();
        JsonNode entry = om.readTree(lines.get(1));
        assertThat(entry.has("lsn")).isTrue();
        assertThat(entry.get("tx_id").asText()).isIn("tx1", "tx2");
    }
}
