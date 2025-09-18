package com.kenfukuda.dashboard.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BackupRestoreTest {

    @Test
    public void backupAndRestoreShouldPreserveDbFile() throws Exception {
        Path db = Path.of("data", "backup_test.db");
        Path bak = Path.of("data", "backup_test.db.bak");
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}

        // create db and add a table and a row
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE test_backup (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
                s.execute("INSERT INTO test_backup (name) VALUES ('before')");
            }
        }

        BackupManager bm = new BackupManager(db.toString());
        Path out = bm.backup(bak);
        assertTrue(Files.exists(out));

        // modify original db
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("INSERT INTO test_backup (name) VALUES ('after')");
            }
        }

        // restore
        bm.restore(bak);

        // verify restored DB only has the original row
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT name FROM test_backup ORDER BY id ASC")) {
                assertTrue(rs.next());
                assertEquals("before", rs.getString(1));
                assertFalse(rs.next());
            }
        }

        // cleanup
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}
    }

    @Test
    public void backupShouldPreserveTableCountsAndContentHashes() throws Exception {
        Path db = Path.of("data", "backup_test2.db");
        Path bak = Path.of("data", "backup_test2.db.bak");
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}

        // create db and add tables and rows
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE t1 (id INTEGER PRIMARY KEY, name TEXT)");
                s.execute("CREATE TABLE t2 (k TEXT PRIMARY KEY, v BLOB)");
                s.execute("INSERT INTO t1 (name) VALUES ('alice')");
                s.execute("INSERT INTO t1 (name) VALUES ('bob')");
                s.execute("INSERT INTO t2 (k, v) VALUES ('a', x'0102')");
            }
        }

        // snapshot counts & hashes before backup
        List<TableSnapshot> before = snapshotDb(db.toString());

        BackupManager bm = new BackupManager(db.toString());
        Path out = bm.backup(bak);
        assertTrue(Files.exists(out));

        // modify original db after backup
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO t1 (name) VALUES ('charlie')");
            }
        }

        // restore from backup
        bm.restore(bak);

        // snapshot after restore
        List<TableSnapshot> after = snapshotDb(db.toString());

        // compare snapshots: table counts and content hashes should match before backup (and after restore)
        assertEquals(before.size(), after.size());
        for (int i=0;i<before.size();i++) {
            TableSnapshot b = before.get(i);
            TableSnapshot a = after.get(i);
            assertEquals(b.tableName, a.tableName);
            assertEquals(b.rowCount, a.rowCount);
            assertEquals(b.hashBase64, a.hashBase64);
        }

        // cleanup
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}
    }

    private static class TableSnapshot {
        final String tableName;
        final long rowCount;
        final String hashBase64;
        TableSnapshot(String tableName, long rowCount, String hashBase64) { this.tableName = tableName; this.rowCount = rowCount; this.hashBase64 = hashBase64; }
    }

    // create a simple snapshot: list of tables (excluding sqlite internal) with row counts and a simple SHA-256 over concatenated row JSONs
    private List<TableSnapshot> snapshotDb(String dbPath) throws Exception {
        List<TableSnapshot> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (Statement s = c.createStatement()) {
                try (ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                    while (rs.next()) {
                        String t = rs.getString(1);
                        long count = 0;
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        try (ResultSet rs2 = c.createStatement().executeQuery("SELECT * FROM " + t + " ORDER BY ROWID")) {
                            java.sql.ResultSetMetaData mdm = rs2.getMetaData();
                            int cols = mdm.getColumnCount();
                            while (rs2.next()) {
                                count++;
                                StringBuilder sb = new StringBuilder();
                                for (int i=1;i<=cols;i++) {
                                    Object o = rs2.getObject(i);
                                    if (o == null) {
                                        sb.append("null").append('|');
                                    } else if (o instanceof byte[]) {
                                        sb.append(Base64.getEncoder().encodeToString((byte[]) o)).append('|');
                                    } else {
                                        sb.append(o.toString()).append('|');
                                    }
                                }
                                md.update(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                        }
                        String base64 = Base64.getEncoder().encodeToString(md.digest());
                        out.add(new TableSnapshot(t, count, base64));
                    }
                }
            }
        }
        return out;
    }

    @Test
    public void backupAndRestoreShouldWorkInWalMode() throws Exception {
        Path db = Path.of("data", "backup_test_wal.db");
        Path bak = Path.of("data", "backup_test_wal.db.bak");
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}

        // create db in WAL mode and add a table and a row
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("CREATE TABLE test_backup (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
                s.execute("INSERT INTO test_backup (name) VALUES ('before')");
            }
        }

        BackupManager bm = new BackupManager(db.toString());
        Path out = bm.backup(bak);
        assertTrue(Files.exists(out));

        // modify original db
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement()) {
                s.execute("INSERT INTO test_backup (name) VALUES ('after')");
            }
        }

        // restore
        bm.restore(bak);

        // verify restored DB only has the original row
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
            try (java.sql.Statement s = c.createStatement(); java.sql.ResultSet rs = s.executeQuery("SELECT name FROM test_backup ORDER BY id ASC")) {
                assertTrue(rs.next());
                assertEquals("before", rs.getString(1));
                assertFalse(rs.next());
            }
        }

        // cleanup
        try { Files.deleteIfExists(db); } catch (Exception ignore) {}
        try { Files.deleteIfExists(bak); } catch (Exception ignore) {}
    }
}
