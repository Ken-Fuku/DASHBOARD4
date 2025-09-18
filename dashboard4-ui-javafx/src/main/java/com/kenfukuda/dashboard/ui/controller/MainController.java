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

    private ApiClient apiClient = new ApiClient();

    @FXML
    public void initialize() {
        statusLabel.setText("Ready");
        fetchButton.setOnAction(e -> fetchReport());
    }

    private void fetchReport() {
        statusLabel.setText("Fetching...");
        // In Phase1 we only call API; business logic stays in app-core
        String res = apiClient.fetchSample();
        statusLabel.setText(res != null ? res : "No data");
    }
}
