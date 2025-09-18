package com.kenfukuda.dashboard.domain.model;

public class ChangeLogEntry {
    private long lsn;
    private String txId;
    private String tableName;
    private String pkJson;
    private String op;
    private String payload;
    private int tombstone;
    private String createdAt;
    private String sourceNode;

    public ChangeLogEntry() {}

    public ChangeLogEntry(long lsn, String txId, String tableName, String pkJson, String op, String payload, int tombstone, String createdAt, String sourceNode) {
        this.lsn = lsn;
        this.txId = txId;
        this.tableName = tableName;
        this.pkJson = pkJson;
        this.op = op;
        this.payload = payload;
        this.tombstone = tombstone;
        this.createdAt = createdAt;
        this.sourceNode = sourceNode;
    }

    // backward-compatible constructor (sourceNode defaults to null)
    public ChangeLogEntry(long lsn, String txId, String tableName, String pkJson, String op, String payload, int tombstone, String createdAt) {
        this(lsn, txId, tableName, pkJson, op, payload, tombstone, createdAt, null);
    }

    public long getLsn() { return lsn; }
    public String getTxId() { return txId; }
    public String getTableName() { return tableName; }
    public String getPkJson() { return pkJson; }
    public String getOp() { return op; }
    public String getPayload() { return payload; }
    public int getTombstone() { return tombstone; }
    public String getCreatedAt() { return createdAt; }
    public String getSourceNode() { return sourceNode; }

    public void setLsn(long lsn) { this.lsn = lsn; }
    public void setTxId(String txId) { this.txId = txId; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public void setPkJson(String pkJson) { this.pkJson = pkJson; }
    public void setOp(String op) { this.op = op; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setTombstone(int tombstone) { this.tombstone = tombstone; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setSourceNode(String sourceNode) { this.sourceNode = sourceNode; }

    @Override
    public String toString() {
        return "ChangeLogEntry{" +
                "lsn=" + lsn +
                ", txId='" + txId + '\'' +
                ", tableName='" + tableName + '\'' +
                ", pkJson='" + pkJson + '\'' +
                ", op='" + op + '\'' +
                ", payload='" + (payload != null ? (payload.length() > 200 ? payload.substring(0,200) + "..." : payload) : null) + '\'' +
                ", tombstone=" + tombstone +
                ", createdAt='" + createdAt + '\'' +
                ", sourceNode='" + sourceNode + '\'' +
                '}';
    }
}
