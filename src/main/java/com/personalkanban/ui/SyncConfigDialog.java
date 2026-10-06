package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.port.SyncRepository;
import com.personalkanban.application.sync.RemoteBoardInfo;
import com.personalkanban.application.sync.SyncException;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Online-sync (API) configuration — the single place where everything
 * about the Zotero-style sync target is set up: the server URL, the
 * API key, a connection test and the remote catalog. Values are
 * prefilled from the current database's settings; OK stores them via
 * {@link AppContext#configureSync}, Cancel leaves them untouched.
 * "Disable sync" clears both fields, which turns sync off.
 *
 * <p>HTTP probes run on a background thread (a hung server must not
 * freeze the UI); results come back through the task callbacks,
 * which JavaFX runs on the application thread.</p>
 */
final class SyncConfigDialog {

    /** What OK produces; the caller decides how to persist it. */
    record SyncSettings(String url, String key) {
    }

    private final I18n i18n;
    private final AppContext context;
    private final Dialogs dialogs;

    private TextField urlField;
    private PasswordField keyField;
    private Label statusLabel;
    private ListView<RemoteBoardInfo> catalogList;
    private Task<List<RemoteBoardInfo>> running;

    SyncConfigDialog(I18n i18n, AppContext context) {
        this.i18n = i18n;
        this.context = context;
        this.dialogs = new Dialogs(i18n);
    }

    Optional<SyncSettings> show() {
        urlField = new TextField(context.syncServerUrl().orElse(""));
        urlField.setPromptText(i18n.text("sync.url.example"));
        urlField.setMaxWidth(Double.MAX_VALUE);

        keyField = new PasswordField();
        keyField.setText(context.syncApiKey().orElse(""));
        keyField.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label(" ");
        statusLabel.getStyleClass().add("prefs-file-label");

        catalogList = new ListView<>();
        catalogList.setPrefHeight(150);
        catalogList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(RemoteBoardInfo info, boolean empty) {
                super.updateItem(info, empty);
                if (empty || info == null) {
                    setText(null);
                } else {
                    setText(info.name() + "  ·  v" + info.version()
                            + "  ·  " + info.updatedAt()
                            + "  ·  " + info.id());
                }
            }
        });

        Button test = new Button(i18n.text("sync.test"));
        Button catalog = new Button(i18n.text("sync.catalog"));
        Button clear = new Button(i18n.text("sync.clear"));
        test.setOnAction(e -> probe(this::renderCatalog));
        catalog.setOnAction(e -> probe(this::renderCatalog));
        clear.setOnAction(e -> onClear());
        HBox buttons = new HBox(10, test, catalog, clear);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label(i18n.text("sync.url")), 0, 0);
        form.add(urlField, 1, 0);
        form.add(new Label(i18n.text("sync.key")), 0, 1);
        form.add(keyField, 1, 1);
        form.add(buttons, 1, 2);

        VBox content = new VBox(12, form,
                new Label(i18n.text("sync.catalog.here")), catalogList, statusLabel);
        VBox.setVgrow(catalogList, Priority.ALWAYS);

        Dialog<SyncSettings> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("sync.title"));
        dialog.setHeaderText(i18n.text("sync.header"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Dialogs.makeResizable(dialog, 560, 420);

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new SyncSettings(urlField.getText(), keyField.getText())
                : null);
        return dialog.showAndWait();
    }

    /**
     * Probes the entered URL/key: runs {@code onOk} with the remote
     * catalog on success, or reports the failure on the status line.
     */
    private void probe(java.util.function.Consumer<List<RemoteBoardInfo>> onOk) {
        if (running != null && running.isRunning()) {
            return;
        }
        String url = urlField.getText().strip();
        String key = keyField.getText();
        if (url.isEmpty() || key == null || key.isBlank()) {
            statusLabel.setText(i18n.text("sync.test.noconfig"));
            return;
        }
        Optional<SyncRepository> repository = context.syncRepositoryFor(url, key);
        if (repository.isEmpty()) {
            statusLabel.setText(i18n.text("sync.test.badurl"));
            return;
        }
        Task<List<RemoteBoardInfo>> task = new Task<>() {
            @Override
            protected List<RemoteBoardInfo> call() {
                return repository.get().catalog();
            }
        };
        running = task;
        task.setOnSucceeded(event -> {
            running = null;
            onOk.accept(task.getValue());
            statusLabel.setText(i18n.text("sync.test.ok", task.getValue().size()));
        });
        task.setOnFailed(event -> {
            running = null;
            String message = syncFailure(task.getException());
            statusLabel.setText(message);
            dialogs.error(message);
        });
        new Thread(task, "sync-probe").start();
    }

    /** Clears the stored URL/key, turning sync off for this database. */
    private void onClear() {
        context.configureSync("", "");
        urlField.clear();
        keyField.clear();
        catalogList.getItems().clear();
        statusLabel.setText(i18n.text("sync.cleared"));
    }

    private void renderCatalog(List<RemoteBoardInfo> boards) {
        catalogList.getItems().setAll(boards);
        if (boards.isEmpty()) {
            statusLabel.setText(i18n.text("sync.empty"));
        }
    }

    /** Maps a sync failure to the localized, actionable message. */
    private String syncFailure(Throwable failure) {
        if (failure instanceof SyncException sync) {
            return switch (sync.kind()) {
                case NETWORK -> i18n.text("sync.error.network", failure.getMessage());
                case UNAUTHORIZED -> i18n.text("sync.error.unauthorized");
                case BAD_REQUEST -> i18n.text("sync.error.bad_request");
                case TOO_LARGE -> i18n.text("sync.error.too_large");
                case SERVER -> i18n.text("sync.error.server", sync.statusCode());
            };
        }
        String message = failure == null || failure.getMessage() == null
                ? String.valueOf(failure)
                : failure.getMessage();
        return i18n.text("sync.error.network", message);
    }
}
