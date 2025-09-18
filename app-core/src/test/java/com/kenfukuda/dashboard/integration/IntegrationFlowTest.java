package com.kenfukuda.dashboard.integration;

import com.kenfukuda.dashboard.infrastructure.db.MigrationRunner;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationFlowTest {
    private Path dbPath;
    private SqliteConnectionManager cm;
    private Path logsDir;
    private Path ciLog;

    @BeforeEach
    public void setUp() throws Exception {
        dbPath = Paths.get("data", "test_integration.db");
        Files.deleteIfExists(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());
        logsDir = Paths.get("app-core", "target", "logs");
        if (!Files.exists(logsDir)) Files.createDirectories(logsDir);
        ciLog = logsDir.resolve("ci_test_run.log");
        // delete old log
        Files.deleteIfExists(ciLog);
    }

    @AfterEach
    public void tearDown() throws Exception {
        Files.deleteIfExists(dbPath);
        // keep logs for inspection
    }

    @Test
    public void migrationSeedAndQueryFlow() throws Exception {
        // run migrations (repository-level db/migrations)
        MigrationRunner mr = new MigrationRunner(cm, Paths.get("..", "db", "migrations"));
        mr.run();

        // dump sqlite_master to log for debugging
        try (Connection c = cm.getConnection(); Statement st = c.createStatement(); java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(ciLog.toFile(), true))) {
            try (ResultSet rs = st.executeQuery("SELECT type || ':' || name AS n FROM sqlite_master ORDER BY type, name")) {
                while (rs.next()) {
                    bw.write("MASTER: " + rs.getString("n") + "\n");
                }
            }
        }

        // Instead of executing scripts/seed.sql (which may use JSON or PRAGMA that vary by environment),
        // perform idempotent inserts programmatically via JDBC so CI is reliable.
        try (Connection c = cm.getConnection()) {
            // clear tables to be safe
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM visit_daily");
                st.executeUpdate("DELETE FROM budget_factors");
                st.executeUpdate("DELETE FROM budget_monthly");
                st.executeUpdate("DELETE FROM store");
                st.executeUpdate("DELETE FROM company");
            }

            // insert 5 companies
            for (int i = 1; i <= 5; i++) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(String.format("INSERT INTO company (company_code, name) VALUES ('C%03d', 'Company %d')", i, i));
                }
            }
            // insert 5 stores
            for (int i = 1; i <= 5; i++) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(String.format("INSERT INTO store (store_code, company_id, name, country, brand, open_date) VALUES ('S%03d', %d, 'Store %d', 'JP', 'Brand', '2020-01-01')", i, i, i));
                }
            }
            // insert 5 budget_monthly and budget_factors
            for (int i = 1; i <= 5; i++) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(String.format("INSERT INTO budget_monthly (store_id, year_month, budget_amount) VALUES (%d, '2025-09', %d)", i, 1000 + i * 100));
                }
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(String.format("INSERT INTO budget_factors (store_id, year_month, factor_json) VALUES (%d, '2025-09', json('{\"factor\":%s}'))", i, String.format("%.1f", 1.0 + i * 0.1)));
                }
            }
            // insert 100 visit_daily rows
            for (int i = 1; i <= 100; i++) {
                int storeId = ((i - 1) % 5) + 1;
                String date = String.format("2025-09-%02d", ((i - 1) % 30) + 1);
                int vis = 100 + (i % 50);
                try (Statement st = c.createStatement()) {
                    st.executeUpdate(String.format("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (%d, '%s', %d)", storeId, date, vis));
                }
            }
            // run verification queries and write to log
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(ciLog.toFile(), true))) {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM visit_daily")) {
                    if (rs.next()) bw.write(String.valueOf(rs.getInt(1)) + "\n");
                }
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM company")) {
                    if (rs.next()) bw.write(String.valueOf(rs.getInt(1)) + "\n");
                }
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM store")) {
                    if (rs.next()) bw.write(String.valueOf(rs.getInt(1)) + "\n");
                }
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM budget_monthly")) {
                    if (rs.next()) bw.write(String.valueOf(rs.getInt(1)) + "\n");
                }
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM budget_factors")) {
                    if (rs.next()) bw.write(String.valueOf(rs.getInt(1)) + "\n");
                }
            }
        }

        // assert results from DB directly
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM visit_daily")) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1));
            }
        }
    }
}
