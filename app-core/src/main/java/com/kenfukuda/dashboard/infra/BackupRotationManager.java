package com.kenfukuda.dashboard.infra;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BackupRotationManager
 * - produces timestamped backup filenames
 * - enforces max retention count and max age (days)
 * - provides a convenience method to create a backup using BackupManager
 */
public class BackupRotationManager {

    private final Path backupDir;
    private final int maxBackups; // keep at most this many files
    private final Duration maxAge; // files older than this will be removed
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    public BackupRotationManager(Path backupDir, int maxBackups, Duration maxAge) {
        this.backupDir = backupDir;
        this.maxBackups = maxBackups;
        this.maxAge = maxAge;
    }

    public Path createBackupWithRotation(BackupManager backupManager, Path dbPath) throws Exception {
        // ensure dir exists
        Files.createDirectories(backupDir);
        String ts = fmt.format(Instant.now());
        String fileName = String.format("backup-%s.db", ts);
        Path dest = backupDir.resolve(fileName);
        Path out = backupManager.backup(dest);
        rotateIfNeeded();
        return out;
    }

    public void rotateIfNeeded() throws IOException {
        if (!Files.exists(backupDir)) return;
        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir, "backup-*.db")) {
            for (Path p : ds) backups.add(p);
        }
        // sort by modified time descending (newest first)
        List<Path> sorted = backups.stream().sorted(Comparator.comparing(this::getFileTime).reversed()).collect(Collectors.toList());

        // remove by age
        Instant cutoff = Instant.now().minus(maxAge);
        for (Path p : new ArrayList<>(sorted)) {
            Instant m = getFileTime(p);
            if (m.isBefore(cutoff)) {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            }
        }

        // re-list after age cleanup
        backups.clear();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir, "backup-*.db")) { for (Path p: ds) backups.add(p); }
        sorted = backups.stream().sorted(Comparator.comparing(this::getFileTime).reversed()).collect(Collectors.toList());

        // enforce max count
        for (int i = maxBackups; i < sorted.size(); i++) {
            Path p = sorted.get(i);
            try { Files.deleteIfExists(p); } catch (Exception ignore) {}
        }
    }

    private Instant getFileTime(Path p) {
        try {
            BasicFileAttributes attr = Files.readAttributes(p, BasicFileAttributes.class);
            return attr.lastModifiedTime().toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
