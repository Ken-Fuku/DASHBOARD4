package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.domain.model.ChangeLogEntry;
import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ChangeLogRepository {
    private final SqliteConnectionManager cm;

    public ChangeLogRepository(SqliteConnectionManager cm) {
        this.cm = cm;
    }

    // insert entry, return generated lsn
    public long insert(ChangeLogEntry e) throws Exception {
        String sql = "INSERT INTO change_log(tx_id, table_name, pk_json, op, payload, tombstone, created_at) VALUES(?, ?, ?, ?, ?, ?, datetime('now'))";
        try (Connection c = cm.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getTxId());
            ps.setString(2, e.getTableName());
            ps.setString(3, e.getPkJson());
            ps.setString(4, e.getOp());
            ps.setString(5, e.getPayload());
            ps.setInt(6, e.getTombstone());
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

    // list change_log entries with lsn > fromLsn
    public List<ChangeLogEntry> listFrom(long fromLsn) throws Exception {
        String sql = "SELECT lsn, tx_id, table_name, pk_json, op, payload, tombstone, created_at FROM change_log WHERE lsn > ? ORDER BY lsn ASC";
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
                            rs.getString("created_at")
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
}
