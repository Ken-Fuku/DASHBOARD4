package com.kenfukuda.dashboard.application.dto.report;

import java.time.LocalDate;

/**
 * C1 レポートの行 DTO スケルトン
 */
public class C1Row {
    private Long companyId;
    private String companyName;
    private Long storeId;
    private String storeCode;
    private String storeName;
    private LocalDate date; // 日付（Daily の場合）

    private Integer visitors; // 来店客数（実績）
    private Integer budget;   // 予算（日次換算または月割）
    private Integer prevYearVisitors; // 前年同日値（null 許容）

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getVisitors() {
        return visitors;
    }

    public void setVisitors(Integer visitors) {
        this.visitors = visitors;
    }

    public Integer getBudget() {
        return budget;
    }

    public void setBudget(Integer budget) {
        this.budget = budget;
    }

    public Integer getPrevYearVisitors() {
        return prevYearVisitors;
    }

    public void setPrevYearVisitors(Integer prevYearVisitors) {
        this.prevYearVisitors = prevYearVisitors;
    }
}
