package com.kenfukuda.dashboard.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.application.usecase.ReportQueryService;
import com.kenfukuda.dashboard.application.dto.report.C1Request;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.cli.util.CsvExporter;

import java.time.YearMonth;
import java.util.concurrent.Callable;

@Command(name = "report-c1", mixinStandardHelpOptions = true, description = "Export C1 report as CSV")
public class ReportC1Command implements Callable<Integer> {

    @Option(names = {"--db"}, description = "Path to SQLite DB", required = true)
    private String dbPath;

    @Option(names = {"--yyyymm"}, description = "Target year-month (YYYY-MM)", required = true)
    private String yyyymm;

    @Option(names = {"--company"}, description = "Company ID (optional)")
    private Long companyId;

    @Option(names = {"--limit"}, description = "Page size")
    private Integer limit = 100;

    @Override
    public Integer call() throws Exception {
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        ReportQueryService svc = new ReportQueryService(cm);

        C1Request req = new C1Request();
        req.setYyyymm(YearMonth.parse(yyyymm));
        req.setCompanyId(companyId);
        req.setLimit(limit);

        // Prepare output file in tools/
        java.time.format.DateTimeFormatter tf = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String ts = java.time.LocalDateTime.now().format(tf);
        java.nio.file.Path toolsDir = java.nio.file.Paths.get("tools");
        java.nio.file.Files.createDirectories(toolsDir);
        java.nio.file.Path outFile = toolsDir.resolve(String.format("report_c1_%s.csv", ts));

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(outFile);
             java.io.BufferedWriter w = java.nio.charset.StandardCharsets.UTF_8 == null ? null : new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8))) {
            // write BOM + header
            os.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
            w.write("company_id,company_name,store_id,store_code,store_name,date,visitors,budget");
            w.newLine();

            String next = null;
            do {
                req.setNextCursor(next);
                PagedResult<C1Row> res = svc.queryC1(req);
                java.util.List<C1Row> items = res.getItems();
                // append page
                CsvExporter.appendC1(items, w);
                next = res.getNextCursor();
            } while (next != null);

            w.flush();
        }

        System.out.println("Wrote report to: " + outFile.toAbsolutePath().toString());
        return 0;
    }
}
