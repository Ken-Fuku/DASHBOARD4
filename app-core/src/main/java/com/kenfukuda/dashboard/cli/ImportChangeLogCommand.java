package com.kenfukuda.dashboard.cli;

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
            // read header meta if present
            String header = r.readLine();
            String clientId = null;
            if (header != null && !header.trim().isEmpty()) {
                JsonNode headerNode = mapper.readTree(header);
                if (headerNode.has("__meta__")) {
                    JsonNode meta = headerNode.get("__meta__");
                    if (meta.has("client_id")) clientId = meta.get("client_id").asText();
                    // if needed in future, meta.to_lsn is available here
                } else {
                    // not a meta header; treat it as first data line
                    // we'll process it below by resetting the reader position: handle by processing 'header' as first line
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
                    // use repo but with explicit connection for transactionality if needed
                    repo.insert(e);
                    conn.commit();
                    seenTx.put(txId, true);
                    existingTx.add(txId);
                    if (lsn > maxAppliedLsn) maxAppliedLsn = lsn;
                } catch (Exception ex) {
                    // rollback this tx only
                    try { if (conn != null) conn.rollback(sp); } catch (Exception rbe) {}
                    System.err.println("Failed to apply tx_id=" + txId + ": " + ex.getMessage());
                    // continue with next tx
                }
            }

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
}
