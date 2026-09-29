package com.personalkanban;

import com.personalkanban.ui.BoardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/**
 * Composition-root entry point. Keeps Application thin: all wiring lives in
 * {@link AppContext}, all behavior in controllers.
 */
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        try {
            AppContext context = AppContext.create();
            BoardController controller = new BoardController(context);

            Scene scene = new Scene(controller.root(), 1200, 720);
            scene.getStylesheets().add(context.themeManager().stylesheet());
            controller.bindScene(scene);

            stage.setTitle("Personal Kanban");
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showFatalError(e);
            throw e;
        }
    }

    private static void showFatalError(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR,
                "Personal Kanban failed to start: " + e.getMessage(), ButtonType.CLOSE);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
