package com.kenfukuda.dashboard.application.usecase;

import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportQueryServiceEdgeCasesTest {
    private SqliteConnectionManager cm;
    private Path dbPath = Path.of("data", "test_report_edge.db");

    @BeforeEach
    public void setUp() throws Exception {
        if (Files.exists(dbPath)) Files.delete(dbPath);
        cm = new SqliteConnectionManager(dbPath.toString());

        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("CREATE TABLE company (id INTEGER PRIMARY KEY, name TEXT)");
            s.execute("CREATE TABLE store (id INTEGER PRIMARY KEY, store_code TEXT, name TEXT, company_id INTEGER)");
            s.execute("CREATE TABLE visit_daily (store_id INTEGER, visit_date TEXT, visitors INTEGER)");
            s.execute("CREATE TABLE budget_monthly (store_id INTEGER, yyyymm TEXT, monthly_budget INTEGER)");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
    }

    @Test
    public void testLimitOnePaging() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO company (id, name) VALUES (1, 'C1')");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (1, 'S1', 'Store 1', 1)");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (2, 'S2', 'Store 2', 1)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (1, '2025-09-03', 10)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (2, '2025-09-02', 20)");
        }

        ReportQueryService svc = new ReportQueryService(cm);
        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.of(2025, 9));
        req.setLimit(1);

        PagedResult<C1Row> p1 = svc.queryC1(req);
        assertEquals(1, p1.getItems().size());
        assertNotNull(p1.getNextCursor());

        C1Request req2 = new C1Request();
        req2.setYyyymm(YearMonth.of(2025, 9));
        req2.setLimit(1);
        req2.setNextCursor(p1.getNextCursor());
        PagedResult<C1Row> p2 = svc.queryC1(req2);
        assertEquals(1, p2.getItems().size());
        assertNull(p2.getNextCursor());
    }

    @Test
    public void testEmptyResult() throws Exception {
        // No data inserted
        ReportQueryService svc = new ReportQueryService(cm);
        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.of(2025, 9));
        req.setLimit(10);

        PagedResult<C1Row> res = svc.queryC1(req);
        assertNotNull(res.getItems());
        assertTrue(res.getItems().isEmpty());
        assertNull(res.getNextCursor());
    }

    @Test
    public void testSameDateOrderingTieBreak() throws Exception {
        try (Connection c = cm.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO company (id, name) VALUES (1, 'C1')");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (5, 'S5', 'Store 5', 1)");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (6, 'S6', 'Store 6', 1)");
            // same date, different store ids (5,6) -> order should be date DESC then store_id ASC
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (6, '2025-09-03', 7)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (5, '2025-09-03', 8)");
        }

        ReportQueryService svc = new ReportQueryService(cm);
        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.of(2025, 9));
        req.setLimit(10);

        PagedResult<C1Row> res = svc.queryC1(req);
        List<C1Row> items = res.getItems();
        assertEquals(2, items.size());
        // store_id should be ascending for same date -> 5 then 6
        assertEquals(5L, items.get(0).getStoreId().longValue());
        assertEquals(6L, items.get(1).getStoreId().longValue());
    }
}
