package com.personalkanban;

import com.personalkanban.ui.AppIcons;
import com.personalkanban.ui.BoardController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.nio.file.Path;

/**
 * Composition-root entry point. Keeps Application thin: all wiring lives in
 * {@link AppContext}, all behavior in controllers.
 *
 * <p>Portable mode: launch with {@code -Dpk.data.dir=<folder>} to keep the
 * SQLite database and undo history inside a chosen folder (e.g. a USB drive)
 * instead of {@code ~/.personalkanban}. The bundled kanban.bat / kanban.sh
 * launchers set this automatically.</p>
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

            stage.titleProperty().bind(controller.titleProperty());
            controller.appendVersionToTitle(AppVersion.stamp());
            // Session 4: a real app icon instead of the default Java cup —
            // this drives the taskbar/window icon on Windows too.
            stage.getIcons().addAll(AppIcons.all());
            stage.setScene(scene);
            // Session 5 (user request): closing the window persists cleanly —
            // every mutation is committed instantly (rollback journal mode),
            // and the connection close is the final tidy-up, never a data save.
            stage.setOnCloseRequest(event -> {
                controller.shutdown();
                Platform.exit();
            });
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

    public static Path dataDirectory() {
        String override = System.getProperty("pk.data.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".personalkanban");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
