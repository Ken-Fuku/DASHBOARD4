package com.kenfukuda.dashboard.infra;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class BackupRotationTest {

    @Test
    public void rotationRemovesOldFilesAndRespectsMaxCount() throws Exception {
        Path tmpDir = Files.createTempDirectory("backup-rot-test");
        try {
            BackupRotationManager mgr = new BackupRotationManager(tmpDir, 2, Duration.ofDays(1));

            // create three fake backup files with differing modified times
            Path a = tmpDir.resolve("backup-older.db");
            Path b = tmpDir.resolve("backup-mid.db");
            Path c = tmpDir.resolve("backup-newer.db");
            Files.writeString(a, "a"); Files.writeString(b, "b"); Files.writeString(c, "c");

            // tweak modified times: a = old, b = mid, c = now
            Files.setLastModifiedTime(a, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofDays(10))));
            Files.setLastModifiedTime(b, java.nio.file.attribute.FileTime.from(Instant.now().minus(Duration.ofHours(2))));
            Files.setLastModifiedTime(c, java.nio.file.attribute.FileTime.from(Instant.now()));

            // rotation should remove 'a' because it's older than 1 day, then ensure max count of 2
            mgr.rotateIfNeeded();
            assertFalse(Files.exists(a), "Old file should be removed by age");

            // now create one more to exceed max count
            Path d = tmpDir.resolve("backup-newest.db");
            Files.writeString(d, "d");
            Files.setLastModifiedTime(d, java.nio.file.attribute.FileTime.from(Instant.now()));

            mgr.rotateIfNeeded();
            long remaining = Files.list(tmpDir).filter(p -> p.getFileName().toString().startsWith("backup-")).count();
            assertTrue(remaining <= 2, "Should keep at most 2 backups");
        } finally {
            // cleanup
            try { Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} }); } catch (Exception ignore) {}
        }
    }

    @Test
    public void createBackupWithRotationCreatesFile() throws Exception {
        Path tmpDir = Files.createTempDirectory("backup-rot-test2");
        Path db = tmpDir.resolve("test.db");
        try {
            // create a small sqlite db
            try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath().toString())) {
                try (java.sql.Statement s = c.createStatement()) { s.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT)"); s.execute("INSERT INTO t (name) VALUES ('x')"); }
            }

            BackupManager bm = new BackupManager(db.toString());
            BackupRotationManager mgr = new BackupRotationManager(tmpDir, 5, Duration.ofDays(30));
            Path out = mgr.createBackupWithRotation(bm, db);
            assertTrue(Files.exists(out));
        } finally {
            try { Files.walk(tmpDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} }); } catch (Exception ignore) {}
        }
    }
}
