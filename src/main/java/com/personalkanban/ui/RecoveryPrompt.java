package com.personalkanban.ui;

import com.personalkanban.application.CorruptDatabaseException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * One question in one place (user request T17 · H-3): when a database fails its
 * integrity check, the app does not open it and offers the <b>newest healthy
 * backup</b> instead. Used by the startup path and by «Database → Open database
 * file», so both behave the same.
 *
 * <p>Nothing is deleted, moved or overwritten: the damaged file stays exactly
 * where it is, and the answer only decides which file the app opens next.</p>
 */
public final class RecoveryPrompt {

    private RecoveryPrompt() {
    }

    /**
     * Asks whether to open the healthy copy. {@code true} means the caller should
     * open {@link CorruptDatabaseException#backup()}.
     *
     * <p>The interface language comes from the system locale here: the saved
     * language lives inside the database that could not be opened.</p>
     */
    public static boolean openBackupInstead(CorruptDatabaseException failure) {
        I18n i18n = new I18n(Locale.getDefault());
        Optional<Path> backup = failure.backup();

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(i18n.text("recovery.corrupt.title"));
        alert.setHeaderText(i18n.text("recovery.corrupt.header"));
        alert.setContentText(backup
                .map(path -> i18n.text("recovery.corrupt.body",
                        failure.databaseFile().toString(), path.toString()))
                .orElseGet(() -> i18n.text("recovery.corrupt.none",
                        failure.databaseFile().toString())));

        ButtonType openBackup = new ButtonType(i18n.text("recovery.open.backup"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType close = new ButtonType(i18n.text("recovery.close"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        // Without a copy there is nothing to offer: only «Cerrar».
        alert.getButtonTypes().setAll(backup.isPresent()
                ? new ButtonType[]{openBackup, close}
                : new ButtonType[]{close});

        return alert.showAndWait().filter(openBackup::equals).isPresent();
    }
}
