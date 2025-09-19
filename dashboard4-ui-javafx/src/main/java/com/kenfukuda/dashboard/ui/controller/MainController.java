package com.kenfukuda.dashboard.ui.controller;

import com.kenfukuda.dashboard.ui.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class MainController {
    @FXML
    private Label statusLabel;

    @FXML
    private Button fetchButton;

    // lazy-initialized to avoid heavy work during controller construction
    private ApiClient apiClient;

    @FXML
    public void initialize() {
        statusLabel.setText("Ready");
        fetchButton.setOnAction(e -> fetchReport());
    }

    private void fetchReport() {
        statusLabel.setText("Fetching...");
        // Allow tests to run synchronously by setting system property
        boolean sync = "true".equalsIgnoreCase(System.getProperty("test.syncFetch"));
        if (sync) {
            try {
                if (apiClient == null) apiClient = new ApiClient();
                String res = apiClient.fetchSample();
                statusLabel.setText(res != null ? res : "No data");
            } catch (Exception ex) {
                statusLabel.setText("Error");
            }
            return;
        }

        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<>() {
            @Override
            protected String call() throws Exception {
                if (apiClient == null) apiClient = new ApiClient();
                return apiClient.fetchSample();
            }
        };

        task.setOnSucceeded(ev -> {
            String res = task.getValue();
            statusLabel.setText(res != null ? res : "No data");
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            String msg = "Fetch failed: " + (ex == null ? "unknown" : ex.toString());
            // show simple alert on error
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                a.setHeaderText("エラー");
                a.setContentText(msg);
                a.showAndWait();
                statusLabel.setText("Error");
            });
        });

        Thread th = new Thread(task, "main-fetch");
        th.setDaemon(true);
        th.start();
    }
}
