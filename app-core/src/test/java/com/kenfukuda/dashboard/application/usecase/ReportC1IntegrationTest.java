package com.kenfukuda.dashboard.application.usecase;

import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.cli.util.CsvExporter;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportC1IntegrationTest {
    private SqliteConnectionManager cm;
    private Path dbPath = Path.of("data", "test_integ_report.db");

    @BeforeEach
    public void setUp() throws Exception {
        if (Files.exists(dbPath)) Files.delete(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());

        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            // Minimal schema
            s.execute("CREATE TABLE company (id INTEGER PRIMARY KEY, name TEXT)");
            s.execute("CREATE TABLE store (id INTEGER PRIMARY KEY, store_code TEXT, name TEXT, company_id INTEGER)");
            s.execute("CREATE TABLE visit_daily (store_id INTEGER, visit_date TEXT, visitors INTEGER)");
            s.execute("CREATE TABLE budget_monthly (store_id INTEGER, yyyymm TEXT, monthly_budget INTEGER)");

            // Seed data for integration test
            s.execute("INSERT INTO company (id, name) VALUES (1, 'C1')");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (1, 'S1', 'Store 1', 1)");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (2, 'S2', 'Store 2', 1)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (1, '2025-09-10', 100)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (2, '2025-09-09', 150)");
            s.execute("INSERT INTO budget_monthly (store_id, yyyymm, monthly_budget) VALUES (1, '2025-09', 3000)");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
        // also remove any CSV outputs
        try {
            Path tools = Path.of("tools");
            if (Files.exists(tools)) {
                try (java.util.stream.Stream<Path> s = Files.list(tools)) {
                    s.filter(p -> p.getFileName().toString().startsWith("report_c1_")).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    @Test
    public void testReportC1ProducesCsvAndMatchesQuery() throws Exception {
        ReportQueryService svc = new ReportQueryService(cm);

        // run service to get rows
        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.of(2025,9));
        req.setLimit(100);
        PagedResult<C1Row> res = svc.queryC1(req);
        List<C1Row> items = res.getItems();
        assertEquals(2, items.size());

        // Now run CLI-style CSV export to tools file
        Path toolsDir = Path.of("tools");
        Files.createDirectories(toolsDir);
        Path out = toolsDir.resolve("report_c1_test_integ.csv");
        try (java.io.OutputStream os = Files.newOutputStream(out);
            java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8))) {
            os.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
            w.write("company_id,company_name,store_id,store_code,store_name,date,visitors,budget");
            w.newLine();
            CsvExporter.appendC1(items, w);
            w.flush();
        }

        // verify CSV exists and has expected number of data lines (header + 2 rows)
        assertTrue(Files.exists(out));
        try (BufferedReader r = new BufferedReader(new FileReader(out.toFile()))) {
            String header = r.readLine();
            assertNotNull(header);
            int dataLines = 0;
            while (r.readLine() != null) dataLines++;
            assertEquals(2, dataLines);
        }
    }
}
