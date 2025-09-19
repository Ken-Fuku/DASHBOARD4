package com.kenfukuda.dashboard.ui;

import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.application.usecase.ReportQueryService;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;

import java.time.YearMonth;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Thin API client that directly calls app-core services in the same JVM.
 */
public class ApiClient {
    private String dbPath;

    public ApiClient() {
        // Prefer system property, then environment variable, then module-local default.
        String prop = System.getProperty("db.path");
        String env = System.getenv("DB_PATH");
        if (prop != null && !prop.isBlank()) {
            this.dbPath = prop;
        } else if (env != null && !env.isBlank()) {
            this.dbPath = env;
        } else {
            this.dbPath = "data/app.db"; // fallback (module-local)
        }
        // try to auto-resolve a better DB file if the chosen one doesn't have expected schema
        try {
            String resolved = probeBestDb(this.dbPath);
            if (resolved != null) {
                this.dbPath = resolved;
            }
        } catch (Exception ex) {
            // ignore and keep current dbPath
            System.out.println("ApiClient: DB probe failed: " + ex);
        }
    }

    /**
     * Return the resolved DB path this client will use (for UI display/debug).
     */
    public String getDbPath() {
        try {
            Path p = Paths.get(this.dbPath).toAbsolutePath().normalize();
            return p.toString();
        } catch (Exception ex) {
            return this.dbPath;
        }
    }

    private String probeBestDb(String initial) {
        // If initial has visit_daily, keep it
        if (hasVisitDaily(initial)) return initial;

        // Candidate list (relative to module folder): parent repo's data, app-core/data, module-local
        String[] candidates = new String[] {
                "../data/app.db",
                "../app-core/data/app.db",
                "data/app.db",
                "./data/app.db"
        };

        for (String c : candidates) {
            try {
                if (hasVisitDaily(c)) {
                    System.out.println("ApiClient: selected DB candidate=" + c);
                    return c;
                }
            } catch (Exception ignore) {}
        }

        return initial;
    }

    private boolean hasVisitDaily(String dbPath) {
        if (dbPath == null) return false;
        Path p = Paths.get(dbPath);
        if (!p.isAbsolute()) p = p.toAbsolutePath().normalize();
        if (!p.toFile().exists()) return false;
        try (Connection conn = new SqliteConnectionManager(p.toString()).getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name='visit_daily'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ex) {
            // treat as not present
            return false;
        }
    }

    public ApiClient(String dbPath) {
        this.dbPath = dbPath;
    }

    public String fetchSample() {
        return "sample-data";
    }

    public PagedResult<C1Row> fetchC1(Long companyId, Long storeId, YearMonth ym, Integer limit, String nextCursor) throws Exception {
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        ReportQueryService svc = new ReportQueryService(cm);
        C1Request req = new C1Request();
        req.setCompanyId(companyId);
        req.setStoreId(storeId);
        req.setYyyymm(ym == null ? YearMonth.now() : ym);
        if (limit != null) req.setLimit(limit);
        req.setNextCursor(nextCursor);
        return svc.queryC1(req);
    }
}

