package com.kenfukuda.dashboard.ui.controller;

import com.kenfukuda.dashboard.ui.ApiClient;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainControllerTest {

    @BeforeAll
    public static void initJfx() {
        // Initialize JavaFX toolkit
        new JFXPanel();
    }

    @Test
    @EnabledIfSystemProperty(named = "runGuiTests", matches = "true")
    public void testFetchUpdatesStatus() throws Exception {
        ApiClient mock = Mockito.mock(ApiClient.class);
        Mockito.when(mock.fetchSample()).thenReturn("ok-from-api");

        MainController controller = new MainController();

        // inject mocked client via reflection
        Field apiField = MainController.class.getDeclaredField("apiClient");
        apiField.setAccessible(true);
        apiField.set(controller, mock);

        // create and inject statusLabel
        Label label = new Label();
        Field statusField = MainController.class.getDeclaredField("statusLabel");
        statusField.setAccessible(true);
        statusField.set(controller, label);

        // call private fetchReport via reflection
        Method m = MainController.class.getDeclaredMethod("fetchReport");
        m.setAccessible(true);
        m.invoke(controller);

        assertEquals("ok-from-api", ((Label) statusField.get(controller)).getText());
    }
}
