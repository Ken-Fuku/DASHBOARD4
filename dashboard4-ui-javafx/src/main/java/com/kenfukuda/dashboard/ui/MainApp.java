package com.kenfukuda.dashboard.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        primaryStage.setTitle("DASHBOARD4 - UI (JavaFX)");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Add simple keyboard shortcut to open C1 view (Ctrl+1)
        scene.getAccelerators().put(new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.DIGIT1, javafx.scene.input.KeyCombination.CONTROL_DOWN), () -> {
            try {
                FXMLLoader fx = new FXMLLoader(getClass().getResource("/fxml/c1.fxml"));
                Parent p = fx.load();
                Stage s = new Stage();
                s.setTitle("C1 Report");
                s.setScene(new Scene(p, 900, 600));
                s.initOwner(primaryStage);
                s.show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
