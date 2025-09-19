package com.kenfukuda.dashboard.application.usecase;

import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.application.util.Pagination;
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

public class ReportQueryServiceTest {
    private SqliteConnectionManager cm;
    private Path dbPath = Path.of("data", "test_report.db");

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

            // Insert sample data
            s.execute("INSERT INTO company (id, name) VALUES (1, 'C1')");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (1, 'S1', 'Store 1', 1)");
            s.execute("INSERT INTO store (id, store_code, name, company_id) VALUES (2, 'S2', 'Store 2', 1)");

            // Two rows on same date (2025-09-03) for store 1 and 2, and one older row
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (1, '2025-09-03', 10)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (2, '2025-09-03', 20)");
            s.execute("INSERT INTO visit_daily (store_id, visit_date, visitors) VALUES (1, '2025-09-02', 5)");
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        try { Files.deleteIfExists(dbPath); } catch (Exception ignored) {}
    }

    @Test
    public void testQueryC1PagingAndCursor() throws Exception {
        ReportQueryService svc = new ReportQueryService(cm);

        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.of(2025, 9));
        req.setLimit(2);

        PagedResult<C1Row> page1 = svc.queryC1(req);
        List<C1Row> items1 = page1.getItems();
        assertNotNull(items1);
        assertEquals(2, items1.size(), "first page should contain 2 items");

        // Because ORDER BY visit_date DESC, store_id ASC, first two should be 2025-09-03 store 1 then store 2
        assertEquals("2025-09-03", items1.get(0).getDate().toString());
        assertEquals(1L, items1.get(0).getStoreId().longValue());
        assertEquals("2025-09-03", items1.get(1).getDate().toString());
        assertEquals(2L, items1.get(1).getStoreId().longValue());

        assertNotNull(page1.getNextCursor(), "nextCursor should be present when more rows exist");

        // Fetch second page using cursor
        C1Request req2 = new C1Request();
        req2.setYyyymm(YearMonth.of(2025, 9));
        req2.setLimit(2);
        req2.setNextCursor(page1.getNextCursor());

        PagedResult<C1Row> page2 = svc.queryC1(req2);
        List<C1Row> items2 = page2.getItems();
        assertNotNull(items2);
        // only one remaining row
        assertEquals(1, items2.size(), "second page should contain remaining 1 item");
        assertEquals("2025-09-02", items2.get(0).getDate().toString());
        assertEquals(1L, items2.get(0).getStoreId().longValue());

        assertNull(page2.getNextCursor(), "no further pages expected");
    }
}
