package com.kenfukuda.dashboard.infrastructure.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqliteConnectionManager {
    private final String url;
    private static final Logger logger = LoggerFactory.getLogger(SqliteConnectionManager.class);

    public SqliteConnectionManager(String dbPath) {
        // Ensure parent directory exists so SQLite can open/create the file
        try {
            Path p = Paths.get(dbPath).toAbsolutePath();
            Path parent = p.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
        } catch (Exception ex) {
            // If directory creation fails, we'll let the later connection call fail with a clear exception
            logger.warn("Warning: failed to create parent directory for DB path: {}", ex.getMessage());
        }

        this.url = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
