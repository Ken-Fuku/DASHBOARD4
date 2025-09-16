package com.kenfukuda.dashboard.infrastructure.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlitePragmaConfigurer {
    private final SqliteConnectionManager cm;
    private static final Logger logger = LoggerFactory.getLogger(SqlitePragmaConfigurer.class);

    public SqlitePragmaConfigurer(SqliteConnectionManager cm) {
        this.cm = cm;
    }

    public void apply(Map<String, String> pragmas) {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            for (Map.Entry<String, String> e : pragmas.entrySet()) {
                String sql = String.format("PRAGMA %s=%s;", e.getKey(), e.getValue());
                s.execute(sql);
                logger.info("Applied PRAGMA: {}", sql);
            }
            // log current values
            try (ResultSet rs = s.executeQuery("PRAGMA journal_mode;")) {
                if (rs.next()) {
                    logger.info("journal_mode={}", rs.getString(1));
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
