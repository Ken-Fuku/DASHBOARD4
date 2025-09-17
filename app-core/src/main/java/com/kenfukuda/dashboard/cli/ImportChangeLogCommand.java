package com.kenfukuda.dashboard.cli;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;
import com.kenfukuda.dashboard.infrastructure.repository.ChangeLogRepository;
import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ImportChangeLogCommand {
    public static void run(String dbPath, String inFile) throws Exception {
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
            // small preflight: find existing tx_ids in DB to skip
            java.util.Set<String> existingTx = new java.util.HashSet<>();
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT tx_id FROM change_log")) {
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) existingTx.add(rs.getString(1));
                }
            }
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
                    // use repo but with explicit connection for transactionality if needed
                    repo.insert(e);
                    conn.commit();
                    seenTx.put(txId, true);
                    existingTx.add(txId);
                } catch (Exception ex) {
                    // rollback this tx only
                    try { if (conn != null) conn.rollback(sp); } catch (Exception rbe) {}
                    System.err.println("Failed to apply tx_id=" + txId + ": " + ex.getMessage());
                    // continue with next tx
                }
            }
        }
        System.out.println("Import complete from " + inFile);
        try { if (conn != null) conn.close(); } catch (Exception e) {}
    }
}
