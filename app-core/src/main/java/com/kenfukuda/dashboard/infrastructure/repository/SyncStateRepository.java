package com.kenfukuda.dashboard.infrastructure.repository;

import com.kenfukuda.dashboard.infrastructure.db.SqliteConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SyncStateRepository {
    private final SqliteConnectionManager cm;

    public SyncStateRepository(SqliteConnectionManager cm) {
        this.cm = cm;
    }

    public Long getLastLsn(String clientId) throws Exception {
        String sql = "SELECT last_lsn FROM sync_state WHERE client_id = ?";
        try (Connection c = cm.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("last_lsn");
            }
        }
        return null;
    }

    // upsert last_lsn for client
    public void upsertLastLsn(String clientId, long lastLsn) throws Exception {
        String sql = "INSERT INTO sync_state(client_id, last_lsn, updated_at) VALUES(?, ?, datetime('now')) ON CONFLICT(client_id) DO UPDATE SET last_lsn = excluded.last_lsn, updated_at = excluded.updated_at";
        try (Connection c = cm.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setLong(2, lastLsn);
            ps.executeUpdate();
        }
    }
}
