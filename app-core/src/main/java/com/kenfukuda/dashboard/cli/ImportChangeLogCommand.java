package com.kenfukuda.dashboard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository;
import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import com.kenfukuda.dashboard.infrastructure.repository.SyncStateRepository;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Command(name = "import-changelog", description = "Import change log JSONL file into local DB")
public class ImportChangeLogCommand implements Runnable {

    @Option(names = {"--db"}, required = true, description = "Path to SQLite DB file")
    private String dbPath;

    @Option(names = {"--in"}, required = true, description = "Input JSONL file to import")
    private String inFile;

    @Option(names = {"--client-id"}, required = false, description = "Client ID to set in sync_state; overrides file meta if provided")
    private String clientIdOverride;

    public void run() {
        try {
            // prefer CLI-provided clientId when present
            if (clientIdOverride != null && !clientIdOverride.isEmpty()) {
                // call programmatic run with override by setting metadata after parsing file
                runWithClientId(dbPath, inFile, clientIdOverride);
            } else {
                run(dbPath, inFile);
            }
        } catch (Exception ex) {
            System.err.println("Import failed: " + ex.getMessage());
            System.exit(2);
        }
    }

    // kept for programmatic compatibility: delegate to main overload (no mainNodeId)
    public static void run(String dbPath, String inFile) throws Exception {
        runWithMainNodeId(dbPath, inFile, (String) null);
    }

    // overload that accepts a mainNodeId; when provided, incoming entries whose __meta__.source_node
    // equals mainNodeId will be considered incomingFromMain=true for conflict resolution.
    public static void runWithMainNodeId(String dbPath, String inFile, String mainNodeId) throws Exception {
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        ChangeLogRepository repo = new ChangeLogRepository(cm);
        java.sql.Connection conn = null;
        try {
            conn = cm.getConnection();
            conn.setAutoCommit(false);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to open DB connection: " + ex.getMessage(), ex);
        }
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), StandardCharsets.UTF_8))) {
            String line;
            Map<String, Boolean> seenTx = new HashMap<>();
            // batch size configuration: system property -Dimport.batchSize or env IMPORT_BATCH_SIZE
            int batchSize = 100;
            try { String v = System.getProperty("import.batchSize"); if (v != null) batchSize = Integer.parseInt(v); } catch (Exception ignore) {}
            try { String v2 = System.getenv("IMPORT_BATCH_SIZE"); if (v2 != null) batchSize = Integer.parseInt(v2); } catch (Exception ignore) {}
            int processedInBatch = 0;
            // small preflight: find existing tx_ids in DB to skip
            java.util.Set<String> existingTx = new java.util.HashSet<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT tx_id FROM change_log")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) existingTx.add(rs.getString(1));
                }
            }
            // read header meta if present
            String header = r.readLine();
            String clientId = null;
            String sourceNodeInFile = null;
            if (header != null && !header.trim().isEmpty()) {
                JsonNode headerNode = mapper.readTree(header);
                if (headerNode.has("__meta__")) {
                    JsonNode meta = headerNode.get("__meta__");
                    if (meta.has("client_id")) clientId = meta.get("client_id").asText();
                    if (meta.has("source_node")) sourceNodeInFile = meta.get("source_node").asText();
                    // if needed in future, meta.to_lsn is available here
                } else {
                    // not a meta header; treat it as first data line
                    // we'll process it below by resetting the reader position: handle by processing 'header' as first data line
                    // push back by processing header variable as first data line
                }
            }

            long maxAppliedLsn = -1L;

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                if (node.has("__meta__")) continue; // skip meta
                long lsn = node.get("lsn").asLong();
                String txId = node.get("tx_id").asText();
                if (seenTx.containsKey(txId)) continue; // in-file duplicate
                if (existingTx.contains(txId)) {
                    System.out.println("Skipping already applied tx_id=" + txId);
                    seenTx.put(txId, true);
                    continue;
                }

                // process this tx in a transaction
                java.sql.Savepoint sp = null;
                try {
                    sp = conn.setSavepoint();

                    ChangeLogEntry e = new ChangeLogEntry();
                    e.setLsn(lsn);
                    e.setTxId(txId);
                    e.setTableName(node.get("table_name").asText());
                    e.setPkJson(node.get("pk_json").toString());
                    e.setOp(node.get("op").asText());
                    e.setPayload(node.has("payload") && !node.get("payload").isNull() ? node.get("payload").toString() : null);
                    e.setTombstone(node.get("tombstone").asInt());
                    e.setCreatedAt(node.get("created_at").asText());
                    // preserve provenance if present
                    try {
                        if (node.has("source_node") && !node.get("source_node").isNull()) {
                            e.setSourceNode(node.get("source_node").asText());
                        } else if (sourceNodeInFile != null) {
                            e.setSourceNode(sourceNodeInFile);
                        }
                    } catch (Exception ignore) {}

                    // conflict resolution: fetch existing latest entry for same table+pk
                    com.kenfukuda.dashboard.domain.service.ConflictResolver resolver = new com.kenfukuda.dashboard.domain.service.ConflictResolver();
                    com.kenfukuda.dashboard.domain.model.ChangeLogEntry existing = null;
                    try {
                        existing = repo.findLatestFor(conn, e.getTableName(), e.getPkJson());
                    } catch (Exception ignore) {}

                    // determine incomingFromMain: if mainNodeId provided, compare with source_node in file or per-entry
                    boolean incomingFromMain = false;
                    if (mainNodeId != null) {
                        // prefer per-entry source_node if present
                        String entrySource = null;
                        try { if (node.has("source_node")) entrySource = node.get("source_node").asText(); } catch (Exception ignore) {}
                        if (entrySource == null) entrySource = sourceNodeInFile;
                        if (entrySource != null && entrySource.equals(mainNodeId)) incomingFromMain = true;
                    }
                    boolean existingFromMain = false;
                    try {
                        if (existing != null && existing.getSourceNode() != null && mainNodeId != null) {
                            existingFromMain = mainNodeId.equals(existing.getSourceNode());
                        }
                    } catch (Exception ignore) {}
                    com.kenfukuda.dashboard.domain.model.ChangeLogEntry winner = resolver.resolve(existing, e, incomingFromMain, existingFromMain);

                    if (winner == e) {
                        // incoming wins -> insert
                        repo.insert(conn, e);
                        // apply the payload to the target table so the client DB reflects the change
                        try {
                            // delegate to repository to apply payload to target table
                            new com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository(new com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager(dbPath)).applyEntry(conn, e);
                        } catch (Exception aex) {
                            System.err.println("Failed to apply change to table " + e.getTableName() + ": " + aex.getMessage());
                        }
                        processedInBatch++;
                        seenTx.put(txId, true);
                        existingTx.add(txId);
                        if (lsn > maxAppliedLsn) maxAppliedLsn = lsn;
                        // commit per batch
                        if (processedInBatch >= batchSize) {
                            conn.commit();
                            // attempt WAL checkpoint to reduce WAL size
                            try (java.sql.Statement s = conn.createStatement()) { s.execute("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignore) {}
                            processedInBatch = 0;
                        }
                    } else {
                        // existing wins -> skip inserting but mark tx as seen to preserve idempotency
                        conn.rollback(sp);
                        System.out.println("Resolved conflict: existing record kept for tx_id=" + txId + " table=" + e.getTableName());
                        seenTx.put(txId, true);
                        existingTx.add(txId);
                    }
                } catch (Exception ex) {
                    // rollback this tx only
                    try { if (conn != null) conn.rollback(sp); } catch (Exception rbe) {}
                    System.err.println("Failed to apply tx_id=" + txId + ": " + ex.getMessage());
                    // continue with next tx
                }
            }

            // commit remaining batch
            try { if (processedInBatch > 0) { conn.commit(); try (java.sql.Statement s = conn.createStatement()) { s.execute("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignore) {} } } catch (Exception ignore) {}

            // after processing, update sync_state.last_lsn in same DB
            if (clientId != null && maxAppliedLsn > 0) {
                SyncStateRepository ssr = new SyncStateRepository(new SqliteConnectionManager(dbPath));
                try {
                    // use the same connection to ensure atomic visibility
                    ssr.upsertLastLsn(conn, clientId, maxAppliedLsn);
                    // commit the upsert
                    conn.commit();
                    System.out.println("Updated sync_state for client_id=" + clientId + " to last_lsn=" + maxAppliedLsn);
                } catch (Exception ex) {
                    try { if (conn != null) conn.rollback(); } catch (Exception rbe) {}
                    System.err.println("Failed to update sync_state for client_id=" + clientId + ": " + ex.getMessage());
                }
            } else if (clientId != null) {
                System.out.println("No applied LSNs; skipping sync_state update for client_id=" + clientId);
            } else {
                System.out.println("No client_id present in import meta; sync_state not updated.");
            }
        }
        System.out.println("Import complete from " + inFile);
        try { if (conn != null) conn.close(); } catch (Exception e) {}
    }

    // ...existing code...

    // backward-compatible signature: run(db,in,clientIdOverride)
    public static void run(String dbPath, String inFile, String clientIdOverride) throws Exception {
        runWithClientId(dbPath, inFile, clientIdOverride);
    }

    // overload to accept CLI-supplied client id
    public static void runWithClientId(String dbPath, String inFile, String clientIdOverride) throws Exception {
        SqliteConnectionManager cm = new SqliteConnectionManager(dbPath);
        ChangeLogRepository repo = new ChangeLogRepository(cm);
        java.sql.Connection conn = null;
        try {
            conn = cm.getConnection();
            conn.setAutoCommit(false);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to open DB connection: " + ex.getMessage(), ex);
        }
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), StandardCharsets.UTF_8))) {
            String line;
            Map<String, Boolean> seenTx = new HashMap<>();
            java.util.Set<String> existingTx = new java.util.HashSet<>();
            int batchSize = 100;
            try { String v = System.getProperty("import.batchSize"); if (v != null) batchSize = Integer.parseInt(v); } catch (Exception ignore) {}
            try { String v2 = System.getenv("IMPORT_BATCH_SIZE"); if (v2 != null) batchSize = Integer.parseInt(v2); } catch (Exception ignore) {}
            int processedInBatch = 0;
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT tx_id FROM change_log")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) existingTx.add(rs.getString(1));
                }
            }

            // read header but ignore client_id inside file because override is provided
            r.readLine(); // consume header (intentionally unused)

            long maxAppliedLsn = -1L;

            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                if (node.has("__meta__")) continue; // skip meta
                long lsn = node.get("lsn").asLong();
                String txId = node.get("tx_id").asText();
                if (seenTx.containsKey(txId)) continue; // in-file duplicate
                if (existingTx.contains(txId)) {
                    System.out.println("Skipping already applied tx_id=" + txId);
                    seenTx.put(txId, true);
                    continue;
                }

                java.sql.Savepoint sp = null;
                try {
                    sp = conn.setSavepoint();

                    ChangeLogEntry e = new ChangeLogEntry();
                    e.setLsn(lsn);
                    e.setTxId(txId);
                    e.setTableName(node.get("table_name").asText());
                    e.setPkJson(node.get("pk_json").toString());
                    e.setOp(node.get("op").asText());
                    e.setPayload(node.has("payload") && !node.get("payload").isNull() ? node.get("payload").toString() : null);
                    e.setTombstone(node.get("tombstone").asInt());
                    e.setCreatedAt(node.get("created_at").asText());
                    // attempt to preserve provenance from per-entry field when present
                    try { if (node.has("source_node") && !node.get("source_node").isNull()) e.setSourceNode(node.get("source_node").asText()); } catch (Exception ignore) {}
                    repo.insert(conn, e);
                    try {
                        new com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository(new com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager(dbPath)).applyEntry(conn, e);
                    } catch (Exception aex) {
                        System.err.println("Failed to apply change to table " + e.getTableName() + ": " + aex.getMessage());
                    }
                    processedInBatch++;
                    seenTx.put(txId, true);
                    existingTx.add(txId);
                    if (lsn > maxAppliedLsn) maxAppliedLsn = lsn;
                    if (processedInBatch >= batchSize) {
                        conn.commit();
                        try (java.sql.Statement s = conn.createStatement()) { s.execute("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignore) {}
                        processedInBatch = 0;
                    }
                } catch (Exception ex) {
                    try { if (conn != null) conn.rollback(sp); } catch (Exception rbe) {}
                    System.err.println("Failed to apply tx_id=" + txId + ": " + ex.getMessage());
                }
            }

            // commit remaining batch
            try { if (processedInBatch > 0) { conn.commit(); try (java.sql.Statement s = conn.createStatement()) { s.execute("PRAGMA wal_checkpoint(TRUNCATE)"); } catch (Exception ignore) {} } } catch (Exception ignore) {}

            if (clientIdOverride != null && maxAppliedLsn > 0) {
                SyncStateRepository ssr = new SyncStateRepository(new SqliteConnectionManager(dbPath));
                try {
                    ssr.upsertLastLsn(conn, clientIdOverride, maxAppliedLsn);
                    conn.commit();
                    System.out.println("Updated sync_state for client_id=" + clientIdOverride + " to last_lsn=" + maxAppliedLsn);
                } catch (Exception ex) {
                    try { if (conn != null) conn.rollback(); } catch (Exception rbe) {}
                    System.err.println("Failed to update sync_state for client_id=" + clientIdOverride + ": " + ex.getMessage());
                }
            }

        }
        System.out.println("Import complete from " + inFile);
        try { if (conn != null) conn.close(); } catch (Exception e) {}
    }
}
