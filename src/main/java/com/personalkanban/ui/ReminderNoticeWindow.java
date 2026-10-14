package com.personalkanban.ui;

import com.personalkanban.application.ReminderPlan;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

/**
 * The message the reminder request asked for (T23 · B10): a window listing the
 * reminder moments that have come due, each with its date, its hour and the
 * <b>card's title</b> — the same message every time, because a reminder is just
 * a card asking to be remembered.
 *
 * <p>Every row can be attended (the reminder leaves the list and never comes
 * back) or opened (the card's detail window); the whole list can be attended in
 * one click, which is a single undo step. A window left open follows the board
 * as the user attends rows, and closes itself when nothing is left due.</p>
 */
final class ReminderNoticeWindow {

    private final I18n i18n;
    private final ThemeManager themeManager;
    private final BoardController board;

    private final Stage stage = new Stage();
    private final VBox rows = new VBox(8);
    private final Label header = new Label();
    private final Button attendAll = new Button();

    private List<ReminderPlan.Due> due = List.of();

    ReminderNoticeWindow(I18n i18n, ThemeManager themeManager, BoardController board) {
        this.i18n = i18n;
        this.themeManager = themeManager;
        this.board = board;

        header.getStyleClass().add("detail-caption");
        header.setWrapText(true);

        attendAll.getStyleClass().add("tool-button");
        attendAll.setOnAction(e -> {
            board.onAcknowledgeAllReminders(due.stream().map(ReminderPlan.Due::cardId).distinct().toList());
            reload();
        });
        Button close = new Button(i18n.text("reminders.notice.close"));
        close.getStyleClass().add("tool-button");
        close.setOnAction(e -> {
            board.muteReminderNotice(due);
            stage.hide();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, attendAll, close);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(6));

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(8, header, scroll);
        content.setPadding(new Insets(10));
        BorderPane layout = new BorderPane();
        layout.setCenter(content);
        layout.setBottom(actions);
        layout.getStyleClass().add("detail-window");

        Scene scene = new Scene(layout, 520, 380);
        scene.getStylesheets().addAll(themeManager.stylesheets());
        stage.setScene(scene);
        stage.setTitle(i18n.text("reminders.window.title"));
        // Closing the window is a \"later\", never a \"done\": the reminders stay
        // pending on their cards and the band keeps showing the count.
        stage.setOnCloseRequest(e -> board.muteReminderNotice(due));
    }

    /** Refreshes the list from the board and shows the window. */
    void show(List<ReminderPlan.Due> entries) {
        due = entries;
        render();
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
    }

    /** Re-reads what is due (after attending, removing or opening a card). */
    private void reload() {
        due = board.dueRemindersNow();
        if (due.isEmpty()) {
            stage.hide();
            return;
        }
        render();
    }

    private void render() {
        header.setText(i18n.text("reminders.notice.header", due.size()));
        attendAll.setText(i18n.text("reminders.ack"));
        attendAll.setDisable(due.isEmpty());
        rows.getChildren().clear();
        for (ReminderPlan.Due entry : due) {
            rows.getChildren().add(row(entry));
        }
    }

    private HBox row(ReminderPlan.Due entry) {
        CardReminder reminder = entry.reminder();
        CheckBox attend = new CheckBox();
        attend.setTooltip(new Tooltip(i18n.text("reminders.ack.one")));
        attend.setOnAction(e -> {
            board.onAcknowledgeReminder(entry.cardId(), reminder.id(), true);
            reload();
        });

        Label when = new Label(stamp(reminder));
        when.getStyleClass().add("detail-caption");
        Label title = new Label(entry.cardTitle());
        title.setWrapText(true);
        VBox text = new VBox(2, when, title);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button open = new Button(i18n.text("reminders.open"));
        open.getStyleClass().add("tool-button");
        open.setOnAction(e -> openCard(entry.cardId()));

        HBox row = new HBox(8, attend, text, open);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Opens the card behind a reminder; the row's own count stays put. */
    private void openCard(CardId cardId) {
        if (board.cardById(cardId).isPresent()) {
            board.onOpenCardDetail(cardId);
        } else {
            // The card was deleted (or the reminder came from another board
            // view) — nothing to open, so drop its rows from the list.
            reload();
        }
    }

    /** \"2026-10-15\" or \"2026-10-15 09:00\": the moment, in the user's calendar. */
    private String stamp(CardReminder reminder) {
        String date = reminder.date().isEqual(LocalDate.now())
                ? i18n.text("reminders.today")
                : reminder.date().toString();
        return reminder.time() == null
                ? i18n.text("reminders.when.allDay", date)
                : i18n.text("reminders.when.at", date, ReminderCalendar.formatTime(reminder.time()));
    }
}
