package com.kenfukuda.dashboard.infrastructure.db;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationRunner {
    private final SqliteConnectionManager cm;
    private final Path migrationsDir;
    private static final Logger logger = LoggerFactory.getLogger(MigrationRunner.class);

    public MigrationRunner(SqliteConnectionManager cm, Path migrationsDir) {
        this.cm = cm;
        this.migrationsDir = migrationsDir;
    }

    public void run() throws Exception {
        if (!Files.exists(migrationsDir)) return;
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            // debug: list existing tables
            try (java.sql.ResultSet rs = s.executeQuery("SELECT name, type FROM sqlite_master WHERE type='table' ORDER BY name")) {
                logger.info("Existing tables in database:");
                while (rs.next()) {
                    logger.info(" - {}", rs.getString("name"));
                }
            } catch (Exception ex) {
                logger.warn("Could not list existing tables: {}", ex.getMessage());
            }
            Files.list(migrationsDir)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            // naive parser: keep trigger bodies (CREATE TRIGGER ... BEGIN ... END;) intact
                            List<String> statements = new ArrayList<>();
                            StringBuilder sb = new StringBuilder();
                            boolean inTrigger = false;
                            for (String line : content.split("\\r?\\n")) {
                                String trimmed = line.trim();
                                if (!inTrigger && trimmed.toUpperCase().startsWith("CREATE TRIGGER")) {
                                    inTrigger = true;
                                }
                                sb.append(line).append("\n");
                                if (inTrigger) {
                                    if (trimmed.toUpperCase().endsWith("END;")) {
                                        statements.add(sb.toString());
                                        sb.setLength(0);
                                        inTrigger = false;
                                    }
                                } else {
                                    if (trimmed.endsWith(";")) {
                                        statements.add(sb.toString());
                                        sb.setLength(0);
                                    }
                                }
                            }
                            // any remaining non-empty chunk
                            String rem = sb.toString().trim();
                            if (!rem.isEmpty()) statements.add(rem);

                            logger.info("Parsed {} statements from {}", statements.size(), p.getFileName());
                            int idx = 0;
                            for (String stmt : statements) {
                                idx++;
                                String sstmt = stmt.trim();
                                String preview = sstmt.replaceAll("\n", " ");
                                if (preview.length() > 200) preview = preview.substring(0, 200) + "...";
                                logger.debug("[{}] {}", idx, preview);
                            }
                            // execute: run CREATE TABLE statements first to ensure indexes/triggers succeed
                            List<String> tableStmts = new ArrayList<>();
                            List<String> otherStmts = new ArrayList<>();
                            for (String stmt : statements) {
                                String sstmt = stmt.trim();
                                if (sstmt.isEmpty()) continue;
                                String cleaned = stripLeadingComments(sstmt);
                                if (cleaned.isEmpty()) continue;
                                String up = cleaned.toUpperCase();
                                if (up.contains("CREATE TABLE") || up.startsWith("ALTER TABLE")) {
                                    tableStmts.add(cleaned);
                                } else {
                                    otherStmts.add(cleaned);
                                }
                            }
                            for (String sstmt : tableStmts) {
                                // strip leading comment lines for execution
                                String execStmt = stripLeadingComments(sstmt);
                                String preview = execStmt.replaceAll("\n", " ");
                                if (preview.length() > 200) preview = preview.substring(0, 200) + "...";
                                logger.info("Executing (tables first): {}", preview);
                                s.execute(execStmt);
                            }
                            for (String sstmt : otherStmts) {
                                String execStmt = stripLeadingComments(sstmt);
                                String preview = execStmt.replaceAll("\n", " ");
                                if (preview.length() > 200) preview = preview.substring(0, 200) + "...";
                                logger.info("Executing: {}", preview);
                                s.execute(execStmt);
                            }
                            logger.info("Applied: {}", p.getFileName());
                        } catch (Exception e) {
                            logger.error("Failed to apply migration {}", p.getFileName(), e);
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private String stripLeadingComments(String stmt) {
        StringBuilder sb = new StringBuilder();
        for (String line : stmt.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("--") || t.isEmpty()) {
                continue;
            }
            // append the rest lines including this one
            sb.append(line).append("\n");
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "" : out;
    }
}
