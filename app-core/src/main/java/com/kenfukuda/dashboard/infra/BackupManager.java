package com.kenfukuda.dashboard.infra;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Simple BackupManager for SQLite: supports copy-based backup using JDBC connection to ensure DB file is flushed.
 * This is intentionally simple: create a backup file copy and provide a restore by replacing the DB file.
 */
public class BackupManager {

    private final String dbPath;

    public BackupManager(String dbPath) {
        this.dbPath = dbPath;
    }

    // Create a file-system level backup by forcing a checkpoint via a connection and copying the file.
    public Path backup(Path dest) throws Exception {
        // ensure parent exists
        Files.createDirectories(dest.getParent());

        // Try VACUUM INTO (preferred when supported) which produces a consistent copy
        String destAbs = dest.toAbsolutePath().toString().replace("\\", "/");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            try (java.sql.Statement s = c.createStatement()) {
                try {
                    // VACUUM INTO may not be supported on older SQLite versions or JDBC wrappers.
                    s.execute("VACUUM INTO '" + destAbs + "'");
                    return dest;
                } catch (Exception vacEx) {
                    // fallback to checkpoint + copy
                    try {
                        s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    } catch (Exception ckex) {
                        // ignore checkpoint failures but log by throwing later if copy also fails
                    }
                }
            }
        }

        // fallback: copy via a temp file then move into place to avoid partial writes
        Path tmp = dest.getParent().resolve(dest.getFileName().toString() + ".tmp");
        try {
            Files.copy(Path.of(dbPath), tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return dest;
        } catch (Exception e) {
            // cleanup tmp if present
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw new Exception("Backup failed for " + dbPath + " -> " + dest + ": " + e.getMessage(), e);
        }
    }

    // Restore backup by copying backup file over the DB file. Caller should ensure DB not in use.
    public void restore(Path src) throws Exception {
        // write to temp then atomically replace
        Path target = Path.of(dbPath);
        Files.createDirectories(target.getParent());
        Path tmp = target.getParent().resolve(target.getFileName().toString() + ".restore.tmp");
        try {
            Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw new Exception("Restore failed from " + src + " -> " + dbPath + ": " + e.getMessage(), e);
        }
    }
}
