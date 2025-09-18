package com.kenfukuda.dashboard.application.usecase;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportQueryService {
    private static final Logger logger = LoggerFactory.getLogger(ReportQueryService.class);
    private final SqliteConnectionManager cm;

    public ReportQueryService(SqliteConnectionManager cm) {
        this.cm = cm;
    }

    private String loadSql(String resourcePath) throws Exception {
        try (InputStream in = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) throw new IllegalArgumentException("SQL resource not found: " + resourcePath);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        }
    }

    public PagedResult<C1Row> queryC1(C1Request req) throws Exception {
        long t0 = System.currentTimeMillis();
        PagedResult<C1Row> res = new PagedResult<>();
        List<C1Row> items = new ArrayList<>();

        // decide date range from YearMonth in request
        YearMonth ym = req.getYyyymm();
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        String sql = loadSql("sql/report/c1.sql");

        // If nextCursor is provided, add keyset WHERE clause before ORDER BY
        String cursor = req.getNextCursor();
        String sqlWithCursor = sql;
        Long cursorStoreId = null;
        String cursorDate = null;
        if (cursor != null) {
            try {
                java.util.Map<String, Object> cur = com.kenfukuda.dashboard.application.util.Pagination.decodeCursor(cursor);
                java.util.List<?> lastValues = (java.util.List<?>) cur.get("lastValues");
                if (lastValues != null && lastValues.size() >= 2) {
                    cursorDate = String.valueOf(lastValues.get(0));
                    cursorStoreId = Long.parseLong(String.valueOf(lastValues.get(1)));
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid nextCursor", ex);
            }
            int orderPos = sqlWithCursor.toUpperCase().indexOf("ORDER BY");
            if (orderPos > 0) {
                String before = sqlWithCursor.substring(0, orderPos);
                String after = sqlWithCursor.substring(orderPos);
                // append keyset condition
                String keyset = " AND (v.visit_date < :cursor_date OR (v.visit_date = :cursor_date AND s.id > :cursor_store_id)) ";
                sqlWithCursor = before + keyset + after;
            }
        }

        try (Connection conn = cm.getConnection()) {

            // Strip comments so placeholders in comments are not treated as parameters
            String cleaned = sqlWithCursor.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*?$", " ");

            // Build positional SQL by replacing named parameters with ? while recording param order
            java.util.List<String> paramNames = new java.util.ArrayList<>();
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile(":([a-zA-Z0-9_]+)");
            java.util.regex.Matcher m = pat.matcher(cleaned);
            StringBuilder sb = new StringBuilder();
            int last = 0;
            while (m.find()) {
                sb.append(cleaned, last, m.start());
                sb.append("?");
                paramNames.add(m.group(1));
                last = m.end();
            }
            sb.append(cleaned.substring(last));
            String prepared = sb.toString();

            logger.debug("[ReportQueryService] Prepared SQL:\n{}", prepared);
            try (PreparedStatement ps2 = conn.prepareStatement(prepared)) {
                int idx = 1;
                // Bind parameters in the same order they appear in SQL
                int limitPlus = Math.max(1, req.getLimit() == null ? 100 : req.getLimit()) + 1;
                for (String name : paramNames) {
                    switch (name) {
                        case "company_id":
                            if (req.getCompanyId() == null) ps2.setNull(idx++, java.sql.Types.BIGINT); else ps2.setLong(idx++, req.getCompanyId());
                            break;
                        case "store_id":
                            if (req.getStoreId() == null) ps2.setNull(idx++, java.sql.Types.BIGINT); else ps2.setLong(idx++, req.getStoreId());
                            break;
                        case "yyyymm_start":
                            ps2.setString(idx++, start.toString());
                            break;
                        case "yyyymm_end":
                            ps2.setString(idx++, end.toString());
                            break;
                        case "cursor_date":
                            if (cursorDate != null) ps2.setString(idx++, cursorDate); else ps2.setNull(idx++, java.sql.Types.VARCHAR);
                            break;
                        case "cursor_store_id":
                            if (cursorStoreId != null) ps2.setLong(idx++, cursorStoreId); else ps2.setNull(idx++, java.sql.Types.BIGINT);
                            break;
                        case "limit":
                            ps2.setInt(idx++, limitPlus);
                            break;
                        default:
                            // unknown placeholder (e.g., inside comments) -> bind null
                            ps2.setNull(idx++, java.sql.Types.VARCHAR);
                            break;
                    }
                }

                try (ResultSet rs = ps2.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        if (count > limitPlus) break; // safety
                        C1Row row = new C1Row();
                        row.setCompanyId(rs.getLong("company_id"));
                        row.setCompanyName(rs.getString("company_name"));
                        row.setStoreId(rs.getLong("store_id"));
                        row.setStoreCode(rs.getString("store_code"));
                        row.setStoreName(rs.getString("store_name"));
                        row.setDate(LocalDate.parse(rs.getString("date")));
                        row.setVisitors(getIntOrNull(rs, "visitors"));
                        row.setBudget(getIntOrNull(rs, "budget"));
                        items.add(row);
                    }
                    // If we fetched more than requested (limit), then produce nextCursor and trim
                    int requested = Math.max(1, req.getLimit() == null ? 100 : req.getLimit());
                    if (items.size() > requested) {
                        // determine last visible row (requested-th)
                        C1Row lastRow = items.get(requested - 1);
                        // create cursor from last row values: date, store_id
                        String lastDate = lastRow.getDate() == null ? null : lastRow.getDate().toString();
                        Long lastStore = lastRow.getStoreId();
                        String nextCursor = null;
                        if (lastDate != null && lastStore != null) {
                            nextCursor = com.kenfukuda.dashboard.application.util.Pagination.encodeCursor(new Object[] { lastDate, lastStore }, "desc");
                        }
                        res.setNextCursor(nextCursor);
                        // trim to requested size
                        List<C1Row> trimmed = new ArrayList<>();
                        for (int i = 0; i < requested && i < items.size(); i++) trimmed.add(items.get(i));
                        items = trimmed;
                    } else {
                        res.setNextCursor(null);
                    }
                }
            }
        }

        res.setItems(items);
        long t1 = System.currentTimeMillis();
        logger.info("queryC1 completed: items={}, timeMs={}", items.size(), (t1 - t0));
        return res;
    }

    private Integer getIntOrNull(ResultSet rs, String col) throws Exception {
        int v = rs.getInt(col);
        if (rs.wasNull()) return null;
        return v;
    }
}
