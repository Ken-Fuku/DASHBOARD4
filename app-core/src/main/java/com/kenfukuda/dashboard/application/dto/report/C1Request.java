package com.kenfukuda.dashboard.application.dto.report;

import java.time.YearMonth;

/**
 * C1 レポートのリクエスト DTO スケルトン
 */
public class C1Request {
    // フィルタ
    private Long companyId; // null = all
    private Long storeId;   // null = all
    private YearMonth yyyymm; // 対象年月

    // ページング
    private Integer limit = 100; // デフォルトページサイズ
    private String nextCursor; // キーセットページング用カーソル

    // 表示オプション
    private boolean includePrevYear = false; // 前年比較を含めるか

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public YearMonth getYyyymm() {
        return yyyymm;
    }

    public void setYyyymm(YearMonth yyyymm) {
        this.yyyymm = yyyymm;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isIncludePrevYear() {
        return includePrevYear;
    }

    public void setIncludePrevYear(boolean includePrevYear) {
        this.includePrevYear = includePrevYear;
    }
}
