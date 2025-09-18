package com.kenfukuda.dashboard.cli.util;

import com.kenfukuda.dashboard.application.dto.report.C1Row;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvExporter {

    // Export list of C1 rows to provided output stream as UTF-8 with BOM
    public static void exportC1(List<C1Row> rows, OutputStream out) throws Exception {
        // Write BOM
        out.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // header
            w.write("company_id,company_name,store_id,store_code,store_name,date,visitors,budget");
            w.newLine();
            for (C1Row r : rows) {
                w.write(escape(String.valueOf(r.getCompanyId())));
                w.write(',');
                w.write(escape(r.getCompanyName()));
                w.write(',');
                w.write(escape(String.valueOf(r.getStoreId())));
                w.write(',');
                w.write(escape(r.getStoreCode()));
                w.write(',');
                w.write(escape(r.getStoreName()));
                w.write(',');
                w.write(escape(r.getDate() == null ? "" : r.getDate().toString()));
                w.write(',');
                w.write(escape(r.getVisitors() == null ? "" : String.valueOf(r.getVisitors())));
                w.write(',');
                w.write(escape(r.getBudget() == null ? "" : String.valueOf(r.getBudget())));
                w.newLine();
            }
            w.flush();
        }
    }

    // Append a page of rows to an existing OutputStream without writing BOM/header
    public static void appendC1(List<C1Row> rows, BufferedWriter w) throws Exception {
        for (C1Row r : rows) {
            w.write(escape(String.valueOf(r.getCompanyId())));
            w.write(',');
            w.write(escape(r.getCompanyName()));
            w.write(',');
            w.write(escape(String.valueOf(r.getStoreId())));
            w.write(',');
            w.write(escape(r.getStoreCode()));
            w.write(',');
            w.write(escape(r.getStoreName()));
            w.write(',');
            w.write(escape(r.getDate() == null ? "" : r.getDate().toString()));
            w.write(',');
            w.write(escape(r.getVisitors() == null ? "" : String.valueOf(r.getVisitors())));
            w.write(',');
            w.write(escape(r.getBudget() == null ? "" : String.valueOf(r.getBudget())));
            w.newLine();
        }
        w.flush();
    }

    private static String escape(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        if (needQuote) {
            return "\"" + out + "\"";
        }
        return out;
    }
}
