package com.kenfukuda.dashboard.ui;

public class ApiClient {
    public String fetchSample() {
        // In Phase1, this should call app-core services directly or via HTTP.
        // Keep as a stub for now.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "sample-data";
    }
}
