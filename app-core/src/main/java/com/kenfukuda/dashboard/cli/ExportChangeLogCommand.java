package com.kenfukuda.dashboard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository;
import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Command(name = "export-changelog", description = "Export change_log entries to JSONL")
public class ExportChangeLogCommand implements Runnable {

    @Option(names = {"--db"}, required = true, description = "Path to SQLite DB file")
    private String dbPath;

    @Option(names = {"--from-lsn"}, required = true, description = "LSN (exclusive) to export from")
    private long fromLsn;

    @Option(names = {"--out"}, required = true, description = "Output JSONL file path")
    private String outFile;

    public void run() {
        try {
            run(dbPath, fromLsn, outFile);
        } catch (Exception ex) {
            System.err.println("Export failed: " + ex.getMessage());
            System.exit(2);
        }
    }

    public static void run(String dbPath, long fromLsn, String outFile) throws Exception {
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        ChangeLogRepository repo = new ChangeLogRepository(cm);
        long max = repo.getMaxLsn();
        if (fromLsn > max) {
            System.out.println("No entries to export. fromLsn="+fromLsn+" max="+max);
            return;
        }
        List<ChangeLogEntry> entries = repo.listFrom(fromLsn);
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            // write meta
            ObjectNode meta = mapper.createObjectNode();
            meta.put("file_version", "1.0");
            meta.put("source_node", "local");
            meta.put("exported_at", java.time.Instant.now().toString());
            meta.put("from_lsn", fromLsn);
            meta.put("to_lsn", max);
            meta.put("tx_grouping", true);
            meta.put("schema_version", "1.0");
            ObjectNode metaWrap = mapper.createObjectNode();
            metaWrap.set("__meta__", meta);
            w.write(mapper.writeValueAsString(metaWrap));
            w.newLine();
            for (ChangeLogEntry e : entries) {
                ObjectNode obj = mapper.createObjectNode();
                obj.put("lsn", e.getLsn());
                obj.put("tx_id", e.getTxId());
                obj.put("table_name", e.getTableName());
                obj.set("pk_json", mapper.readTree(e.getPkJson()));
                obj.put("op", e.getOp());
                if (e.getPayload() != null) obj.set("payload", mapper.readTree(e.getPayload())); else obj.putNull("payload");
                obj.put("tombstone", e.getTombstone());
                obj.put("created_at", e.getCreatedAt());
                obj.put("source_node", "local");
                w.write(mapper.writeValueAsString(obj));
                w.newLine();
            }
        }
        System.out.println("Exported " + entries.size() + " entries to " + outFile);
    }
}
