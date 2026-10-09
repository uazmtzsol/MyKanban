package com.personalkanban.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The board background image must survive CSS application.
 *
 * <p>BoardController paints the background from its constructor — before the
 * Scene exists — and Main then attaches the light/dark stylesheet. JavaFX
 * re-applies the stylesheet when the root enters the scene (and on every
 * stylesheet change), which overwrote the programmatic
 * {@code setBackground(...)} with the {@code .root} color: the user never saw
 * their chosen image. The fix paints via inline {@code setStyle}, the only
 * value with precedence over stylesheets. This test replicates the exact
 * startup order: paint first, build the Scene with light.css, show, snapshot.</p>
 */
class BoardBackgroundCssTest {

    private static final String RED_HEX = "ffff0000";

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        Platform.setImplicitExit(false);
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("FX action must finish").isTrue();
        if (error.get() != null) {
            throw new AssertionError("FX action failed", error.get());
        }
    }

    @Test
    void paintedBackgroundSurvivesStylesheetApplication(@TempDir Path tempDir)
            throws Exception {
        // Solid red PNG — the spec's dim of 0 keeps every pixel pure red.
        Path png = tempDir.resolve("fondo-rojo.png");
        BufferedImage red = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                red.setRGB(x, y, 0xFFFF0000);
            }
        }
        ImageIO.write(red, "png", png.toFile());
        String spec = png.toAbsolutePath() + "|0.00";

        AtomicReference<String> centerPixel = new AtomicReference<>();

        fx(() -> {
            VBox root = new VBox();
            root.getStyleClass().add("board-root");

            // Paint BEFORE the Scene exists — the exact BoardController order.
            BoardController.paintBoardRoot(root, spec, false);

            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(
                    getClass().getResource("/css/light.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            WritableImage snapshot = root.snapshot(null, null);
            centerPixel.set(Integer.toHexString(
                    snapshot.getPixelReader().getArgb(200, 150)));
            stage.hide();
        });

        assertThat(centerPixel.get())
                .as("center pixel must be the painted image, not the stylesheet color")
                .isEqualTo(RED_HEX);
    }

    @Test
    void plainFallbackShowsThemedColor(@TempDir Path tempDir) throws Exception {
        AtomicReference<String> centerPixel = new AtomicReference<>();

        fx(() -> {
            VBox root = new VBox();
            root.getStyleClass().add("board-root");
            BoardController.paintBoardRoot(root, null, false);

            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(
                    getClass().getResource("/css/light.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            WritableImage snapshot = root.snapshot(null, null);
            centerPixel.set(Integer.toHexString(
                    snapshot.getPixelReader().getArgb(200, 150)));
            stage.hide();
        });

        // Light theme plain fill #f5f6f8.
        assertThat(centerPixel.get())
                .as("plain fallback must paint the light theme color")
                .isEqualTo("fff5f6f8");
    }

    @Test
    void missingFileFallsBackToPlainColor(@TempDir Path tempDir) throws Exception {
        AtomicReference<String> centerPixel = new AtomicReference<>();
        String spec = tempDir.resolve("no-existe.png").toAbsolutePath() + "|0.45";

        fx(() -> {
            VBox root = new VBox();
            root.getStyleClass().add("board-root");
            BoardController.paintBoardRoot(root, spec, false);

            Scene scene = new Scene(root, 400, 300);
            scene.getStylesheets().add(
                    getClass().getResource("/css/light.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();

            WritableImage snapshot = root.snapshot(null, null);
            centerPixel.set(Integer.toHexString(
                    snapshot.getPixelReader().getArgb(200, 150)));
            stage.hide();
        });

        assertThat(centerPixel.get())
                .as("missing image must fall back to the plain light color")
                .isEqualTo("fff5f6f8");
    }
}
