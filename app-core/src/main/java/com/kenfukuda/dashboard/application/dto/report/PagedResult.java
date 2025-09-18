package com.kenfukuda.dashboard.application.dto.report;

import java.time.Instant;
import java.util.List;

/**
 * 汎用ページング結果 DTO
 */
public class PagedResult<T> {
    private List<T> items;
    private Long totalCount; // null 可能（計算コスト高い場合）
    private String nextCursor;
    private Instant generatedAt;

    public PagedResult() {
        this.generatedAt = Instant.now();
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
