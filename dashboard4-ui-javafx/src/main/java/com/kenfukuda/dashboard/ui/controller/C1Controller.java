package com.kenfukuda.dashboard.ui.controller;

import com.kenfukuda.dashboard.application.dto.report.C1Row;
import com.kenfukuda.dashboard.application.dto.report.PagedResult;
import com.kenfukuda.dashboard.ui.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public class C1Controller {
    @FXML
    TextField companyIdField;
    @FXML
    TextField storeIdField;
    @FXML
    TextField yyyymmField;
    @FXML
    Button fetchBtn;
    @FXML
    TableView<C1Row> table;
    @FXML
    TableColumn<C1Row, String> colDate;
    @FXML
    TableColumn<C1Row, String> colStore;
    @FXML
    TableColumn<C1Row, Integer> colVisitors;
    @FXML
    TableColumn<C1Row, Integer> colBudget;
    @FXML
    Button prevBtn;
    @FXML
    Button nextBtn;
    @FXML
    Label cursorLabel;
    @FXML
    Label dbPathLabel;

    private final ApiClient api = new ApiClient();
    private String nextCursor = null;

    @FXML
    public void initialize() {
        // show DB path used by ApiClient for debugging
        try {
            String db = api.getDbPath();
            if (dbPathLabel != null) dbPathLabel.setText(db);
        } catch (Exception ignore) {}
        fetchBtn.setOnAction(e -> fetch(false));
        nextBtn.setOnAction(e -> fetch(false));
        prevBtn.setOnAction(e -> fetch(true));
        // configure minimal cell value factories
        colDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDate() == null ? "" : c.getValue().getDate().toString()));
        colStore.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStoreName()));
        colVisitors.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getVisitors()));
        colBudget.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBudget()));
    }

    private void fetch(boolean prev) {
        Long companyId = parseLongOrNull(companyIdField.getText());
        Long storeId = parseLongOrNull(storeIdField.getText());
        YearMonth ym = null;
        try {
            if (yyyymmField.getText() != null && !yyyymmField.getText().isBlank()) ym = YearMonth.parse(yyyymmField.getText());
        } catch (DateTimeParseException ex) {
            showError("Invalid YearMonth format. Use YYYY-MM");
            return;
        }
        // Run fetch in background to avoid blocking UI
        final Long companyIdF = companyId;
        final Long storeIdF = storeId;
        final YearMonth ymF = ym;
        final String cursorF = nextCursor;

        javafx.concurrent.Task<PagedResult<C1Row>> task = new javafx.concurrent.Task<>() {
            @Override
            protected PagedResult<C1Row> call() throws Exception {
                return api.fetchC1(companyIdF, storeIdF, ymF, 100, cursorF);
            }
        };

        task.setOnSucceeded(ev -> {
            PagedResult<C1Row> res = task.getValue();
            table.getItems().clear();
            table.getItems().addAll(res.getItems());
            nextCursor = res.getNextCursor();
            cursorLabel.setText(nextCursor == null ? "" : nextCursor);
        });

        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            String db = api.getDbPath();
            StringWriter sw = new StringWriter();
            if (ex != null) ex.printStackTrace(new PrintWriter(sw));
            String full = "Fetch failed: " + (ex == null ? "unknown" : ex.toString()) + "\nDB: " + db + "\n\n" + sw.toString();
            showError(full);
        });

        Thread th = new Thread(task, "c1-fetch");
        th.setDaemon(true);
        th.start();
    }

    private Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception ex) { return null; }
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("エラー");
            alert.setContentText(msg);

            // If msg contains stack trace, show it in expandable area
            if (msg != null && msg.length() > 200) {
                TextArea textArea = new TextArea(msg);
                textArea.setEditable(false);
                textArea.setWrapText(true);
                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);
                VBox.setVgrow(textArea, Priority.ALWAYS);
                alert.getDialogPane().setExpandableContent(textArea);
            }

            alert.getButtonTypes().setAll(ButtonType.OK);
            alert.showAndWait();
        });
    }
}
