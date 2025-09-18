package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

public class ChangeLogRepository {
    private final SqliteConnectionManager cm;
    private final boolean allowSchemaAlter;

    public ChangeLogRepository(SqliteConnectionManager cm) {
        this.cm = cm;
        // default: read from system property or env var; default false for safety in production
        boolean allow = false;
        try { String v = System.getProperty("allowSchemaAlter"); if (v != null) allow = Boolean.parseBoolean(v); } catch (Exception ignore) {}
        try { String v2 = System.getenv("ALLOW_SCHEMA_ALTER"); if (v2 != null) allow = Boolean.parseBoolean(v2); } catch (Exception ignore) {}
        this.allowSchemaAlter = allow;
    }

    public ChangeLogRepository(SqliteConnectionManager cm, boolean allowSchemaAlter) {
        this.cm = cm;
        this.allowSchemaAlter = allowSchemaAlter;
    }

    // insert entry, return generated lsn
    public long insert(ChangeLogEntry e) throws Exception {
        String sql = "INSERT INTO change_log(tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node) VALUES(?, ?, ?, ?, ?, ?, datetime('now'), ?)";
        try (Connection c = cm.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getTxId());
            ps.setString(2, e.getTableName());
            ps.setString(3, e.getPkJson());
            ps.setString(4, e.getOp());
            ps.setString(5, e.getPayload());
            ps.setInt(6, e.getTombstone());
            ps.setString(7, e.getSourceNode());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    return rs.getLong(1);
                }
            }
            // fallback: last_insert_rowid
            try (ResultSet rs2 = c.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs2.next()) return rs2.getLong(1);
            }
            return -1;
        }
    }

    // insert using an existing Connection (transactional)
    public long insert(Connection conn, ChangeLogEntry e) throws Exception {
        String sql = "INSERT INTO change_log(tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node) VALUES(?, ?, ?, ?, ?, ?, datetime('now'), ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getTxId());
            ps.setString(2, e.getTableName());
            ps.setString(3, e.getPkJson());
            ps.setString(4, e.getOp());
            ps.setString(5, e.getPayload());
            ps.setInt(6, e.getTombstone());
            ps.setString(7, e.getSourceNode());
            ps.executeUpdate();
            try (ResultSet rs2 = conn.createStatement().executeQuery("SELECT last_insert_rowid()")) {
                if (rs2.next()) return rs2.getLong(1);
            }
            return -1;
        }
    }

    // list change_log entries with lsn > fromLsn
    public List<ChangeLogEntry> listFrom(long fromLsn) throws Exception {
        String sql = "SELECT lsn, tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node FROM change_log WHERE lsn > ? ORDER BY lsn ASC";
        List<ChangeLogEntry> out = new ArrayList<>();
        try (Connection c = cm.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fromLsn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChangeLogEntry e = new ChangeLogEntry(
                            rs.getLong("lsn"),
                            rs.getString("tx_id"),
                            rs.getString("table_name"),
                            rs.getString("pk_json"),
                            rs.getString("op"),
                            rs.getString("payload"),
                            rs.getInt("tombstone"),
                            rs.getString("created_at"),
                            rs.getString("source_node")
                    );
                    out.add(e);
                }
            }
        }
        return out;
    }

    public long getMaxLsn() throws Exception {
        try (Connection c = cm.getConnection();
             ResultSet rs = c.createStatement().executeQuery("SELECT MAX(lsn) AS m FROM change_log")) {
            if (rs.next()) return rs.getLong("m");
            return 0L;
        }
    }

    // find the latest change_log entry for the given table and primary-key JSON using an existing connection
    public ChangeLogEntry findLatestFor(Connection conn, String tableName, String pkJson) throws Exception {
        String sql = "SELECT lsn, tx_id, table_name, pk_json, op, payload, tombstone, created_at, source_node FROM change_log WHERE table_name = ? AND pk_json = ? ORDER BY lsn DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, pkJson);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ChangeLogEntry(
                            rs.getLong("lsn"),
                            rs.getString("tx_id"),
                            rs.getString("table_name"),
                            rs.getString("pk_json"),
                            rs.getString("op"),
                            rs.getString("payload"),
                            rs.getInt("tombstone"),
                            rs.getString("created_at"),
                            rs.getString("source_node")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Best-effort: apply a ChangeLogEntry payload to the target table using the provided connection.
     * This is a simplified, generic applier intended for integration tests and simple schemas.
     * It will create a simple table if missing using payload keys as TEXT columns.
     */
    public void applyEntry(Connection conn, com.kenfukuda.dashboard.domain.model.ChangeLogEntry e) throws Exception {
        if (e == null) return;
        String table = e.getTableName();
        String op = e.getOp();
        String payload = e.getPayload();
        String pkJson = e.getPkJson();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payloadMap = null;
        Map<String, Object> pkMap = null;
        try { if (payload != null) payloadMap = mapper.readValue(payload, new TypeReference<Map<String,Object>>(){}); } catch (Exception ex) { payloadMap = null; }
        try { if (pkJson != null) pkMap = mapper.readValue(pkJson, new TypeReference<Map<String,Object>>(){}); } catch (Exception ex) { pkMap = null; }

    // get table schema: column -> declared type; pk columns
    SchemaInfo schema = inspectTableSchema(conn, table);
    java.util.Map<String, String> colTypes = schema.colTypes;
        // ensure table exists: create simple table if missing
        try (PreparedStatement psCheck = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            psCheck.setString(1, table);
            try (ResultSet rs = psCheck.executeQuery()) {
                if (!rs.next()) {
                    if ((payloadMap != null && !payloadMap.isEmpty()) || (pkMap != null && !pkMap.isEmpty())) {
                        StringBuilder colsDef = new StringBuilder();
                        if (payloadMap != null) {
                            for (String k : payloadMap.keySet()) {
                                if (colsDef.length()>0) colsDef.append(", ");
                                colsDef.append(quoteIdentifier(k)).append(" TEXT");
                                colTypes.put(k, "TEXT");
                            }
                        }
                        if ((payloadMap == null || !payloadMap.containsKey("id")) && pkMap != null && pkMap.containsKey("id")) {
                            if (colsDef.length()>0) colsDef.append(", ");
                            colsDef.append("id INTEGER");
                            colTypes.put("id", "INTEGER");
                        }
                        String createSql = "CREATE TABLE " + table + " (" + colsDef.toString() + ")";
                        try (java.sql.Statement s = conn.createStatement()) { s.execute(createSql); }
                    } else if (pkMap != null && pkMap.containsKey("id")) {
                        String createSql = "CREATE TABLE " + table + " (id INTEGER)";
                        try (java.sql.Statement s = conn.createStatement()) { s.execute(createSql); }
                        colTypes.put("id", "INTEGER");
                    }
                }
            }
        }

        // ensure payload and pk columns exist; if missing and allowed, add as TEXT
        if (allowSchemaAlter) {
            if (payloadMap != null) {
                for (String k : payloadMap.keySet()) {
                    if (!colTypes.containsKey(k)) {
                        try (java.sql.Statement s = conn.createStatement()) {
                            s.execute("ALTER TABLE " + table + " ADD COLUMN " + k + " TEXT");
                        } catch (Exception ex) {
                            System.err.println("Failed to ALTER add column " + k + " on table " + table + ": " + ex.getMessage());
                        }
                        colTypes.put(k, "TEXT");
                    }
                }
            }
            if (pkMap != null) {
                for (String k : pkMap.keySet()) {
                    if (!colTypes.containsKey(k)) {
                        try (java.sql.Statement s = conn.createStatement()) {
                            s.execute("ALTER TABLE " + table + " ADD COLUMN " + k + " TEXT");
                        } catch (Exception ex) {
                            System.err.println("Failed to ALTER add PK column " + k + " on table " + table + ": " + ex.getMessage());
                        }
                        colTypes.put(k, "TEXT");
                    }
                }
            }
        }

        if (op.equalsIgnoreCase("I") || op.equalsIgnoreCase("U")) {
            if (payloadMap == null) return;
            // attempt update by pk if available
            if (pkMap != null && !pkMap.isEmpty()) {
                StringBuilder setClause = new StringBuilder();
                for (String k : payloadMap.keySet()) {
                    if (setClause.length()>0) setClause.append(", ");
                    setClause.append(quoteIdentifier(k)).append(" = ?");
                }
                StringBuilder where = new StringBuilder();
                java.util.List<String> pkKeys = determinePkKeys(schema, pkMap);
                where.append(buildWhereClause(pkKeys));
                String sql = "UPDATE " + quoteIdentifier(table) + " SET " + setClause + " WHERE " + where;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx = 1;
                    for (String k : payloadMap.keySet()) {
                        setPreparedValue(ps, idx++, payloadMap.get(k), colTypes.get(k));
                    }
                    idx = bindPkParameters(ps, idx, pkKeys, pkMap, colTypes);
                    int updated = ps.executeUpdate();
                    if (updated > 0) return;
                }
            }

            // insert
            StringBuilder cols = new StringBuilder();
            StringBuilder qmarks = new StringBuilder();
            java.util.List<Object> values = new java.util.ArrayList<>();
            for (String k : payloadMap.keySet()) {
                if (cols.length()>0) { cols.append(", "); qmarks.append(", "); }
                cols.append(quoteIdentifier(k)); qmarks.append("?"); values.add(k);
            }
            String insertSql = "INSERT INTO " + quoteIdentifier(table) + " (" + cols.toString() + ") VALUES (" + qmarks.toString() + ")";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                int idx=1; for (Object keyObj : values) {
                    String k = (String) keyObj;
                    setPreparedValue(ps, idx++, payloadMap.get(k), colTypes.get(k));
                }
                ps.executeUpdate();
            }
        } else if (op.equalsIgnoreCase("D")) {
            if (pkMap != null && !pkMap.isEmpty()) {
                StringBuilder where = new StringBuilder();
                java.util.List<String> pkKeys3 = determinePkKeys(schema, pkMap);
                where.append(buildWhereClause(pkKeys3));
                String sql = "DELETE FROM " + quoteIdentifier(table) + " WHERE " + where;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    int idx=1; 
                    idx = bindPkParameters(ps, idx, pkKeys3, pkMap, colTypes);
                    ps.executeUpdate();
                }
            }
        }
    }

    private static void setPreparedValue(PreparedStatement ps, int idx, Object val, String declaredType) throws java.sql.SQLException {
        if (val == null) { setNullByDeclaredType(ps, idx, declaredType); return; }
        String dt = declaredType == null ? "" : declaredType.toUpperCase();
        try {
            if (dt.contains("INT")) {
                if (val instanceof Number) ps.setLong(idx, ((Number) val).longValue());
                else {
                    String s = val.toString();
                    if (s.isEmpty()) { ps.setObject(idx, null); } else ps.setLong(idx, Long.parseLong(s));
                }
                return;
            } else if (dt.contains("CHAR") || dt.contains("CLOB") || dt.contains("TEXT")) {
                ps.setString(idx, val.toString());
                return;
            } else if (dt.contains("REAL") || dt.contains("FLOA") || dt.contains("DOUB")) {
                if (val instanceof Number) ps.setDouble(idx, ((Number) val).doubleValue());
                else ps.setDouble(idx, Double.parseDouble(val.toString()));
                return;
            } else if (dt.contains("BLOB")) {
                if (val instanceof byte[]) { ps.setBytes(idx, (byte[]) val); }
                else {
                    String s = val.toString();
                    // try base64 decode
                    try {
                        byte[] decoded = java.util.Base64.getDecoder().decode(s);
                        ps.setBytes(idx, decoded);
                    } catch (IllegalArgumentException iae) {
                        ps.setBytes(idx, s.getBytes());
                    }
                }
                return;
            } else {
                // declared type unknown: try to infer from value
                if (val instanceof Number) {
                    if (val instanceof Float || val instanceof Double) ps.setDouble(idx, ((Number) val).doubleValue());
                    else ps.setLong(idx, ((Number) val).longValue());
                } else {
                    String s = val.toString();
                    // try integer
                    try { long lv = Long.parseLong(s); ps.setLong(idx, lv); }
                    catch (NumberFormatException n1) {
                        try { double dv = Double.parseDouble(s); ps.setDouble(idx, dv); }
                        catch (NumberFormatException n2) { ps.setString(idx, s); }
                    }
                }
                return;
            }
        } catch (NumberFormatException nfe) {
            // fallback to string
            String s = val.toString();
            String sl = s.toLowerCase();
            if ("true".equals(sl) || "false".equals(sl)) {
                ps.setBoolean(idx, Boolean.parseBoolean(sl));
            } else if (s.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                // naive date-like string fallback
                ps.setString(idx, s);
            } else {
                ps.setString(idx, s);
            }
        }
    }

    // Build WHERE clause fragment for given PK keys (keys already ordered deterministically)
    private static String buildWhereClause(java.util.List<String> pkKeys) {
        StringBuilder where = new StringBuilder();
        for (String k : pkKeys) {
            if (where.length()>0) where.append(" AND ");
            where.append("(").append(quoteIdentifier(k)).append(" IS ? OR ").append(quoteIdentifier(k)).append(" IS NULL AND ? IS NULL)");
        }
        return where.toString();
    }

    // Helper to set null with an appropriate JDBC type when declaredType is known
    private static void setNullByDeclaredType(PreparedStatement ps, int idx, String declaredType) throws java.sql.SQLException {
        String dt = declaredType == null ? "" : declaredType.toUpperCase();
        if (dt.contains("INT")) ps.setNull(idx, java.sql.Types.BIGINT);
        else if (dt.contains("REAL") || dt.contains("FLOA") || dt.contains("DOUB")) ps.setNull(idx, java.sql.Types.DOUBLE);
        else if (dt.contains("BLOB")) ps.setNull(idx, java.sql.Types.BINARY);
        else ps.setNull(idx, java.sql.Types.VARCHAR);
    }

    // Schema inspection result
    private static class SchemaInfo {
        final java.util.Map<String, String> colTypes = new java.util.HashMap<>();
        final java.util.Set<String> pkColumns = new java.util.HashSet<>();
    }

    // Inspect table schema using PRAGMA table_info and return column types (uppercased) and pk columns
    private SchemaInfo inspectTableSchema(Connection conn, String table) {
        SchemaInfo info = new SchemaInfo();
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info('" + table + "')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String type = rs.getString("type");
                    int pk = rs.getInt("pk");
                    if (name != null) {
                        info.colTypes.put(name, type == null ? "" : type.toUpperCase());
                        if (pk > 0) info.pkColumns.add(name);
                    }
                }
            }
        } catch (Exception ex) {
            // ignore — caller will handle missing table
        }
        return info;
    }

    private static String quoteIdentifier(String id) {
        if (id == null) return "";
        return '"' + id.replace("\"", "\"\"") + '"';
    }

    // Determine deterministic PK key ordering: prefer schema.pkColumns when available, otherwise sort map keys
    private static java.util.List<String> determinePkKeys(SchemaInfo schema, java.util.Map<String, Object> pkMap) {
        java.util.List<String> pkKeys = new java.util.ArrayList<>();
        if (schema != null && schema.pkColumns != null && !schema.pkColumns.isEmpty()) {
            pkKeys.addAll(schema.pkColumns);
        } else if (pkMap != null) {
            pkKeys.addAll(pkMap.keySet());
            java.util.Collections.sort(pkKeys);
        }
        return pkKeys;
    }

    // Bind PK parameters for NULL-safe pair pattern: for each key bind (value, value) where NULL is represented by two params
    private static int bindPkParameters(PreparedStatement ps, int startIdx, java.util.List<String> pkKeys, java.util.Map<String, Object> pkMap, java.util.Map<String, String> colTypes) throws java.sql.SQLException {
        int idx = startIdx;
        for (String k : pkKeys) {
            Object v = pkMap == null ? null : pkMap.get(k);
            String declared = colTypes == null ? null : colTypes.get(k);
            if (v == null) {
                setNullByDeclaredType(ps, idx++, declared);
                setNullByDeclaredType(ps, idx++, declared);
            } else {
                setPreparedValue(ps, idx++, v, declared);
                setPreparedValue(ps, idx++, v, declared);
            }
        }
        return idx;
    }
}
