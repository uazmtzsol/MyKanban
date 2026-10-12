package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.TimeReport;
import com.personalkanban.ui.pdf.CardPdfWriter;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Non-modal time report window (session 14, B1): how much tracked time went
 * into each process and each week of the board on screen, with CSV and PDF
 * export. One shared window per app, like the shortcuts reference.
 *
 * <p>All the numbers come from {@link TimeReport}; this class only draws them
 * and asks for the range. The range picker recomputes the report instead of
 * filtering rows, so switching to "everything" is a real re-query.</p>
 */
final class TimeReportWindow {

    private static Stage openWindow;

    /** One entry of the range picker; {@link TimeReport#ALL} means "everything". */
    private record Range(String labelKey, int weeks) {
    }

    private static final java.util.List<Range> RANGES = java.util.List.of(
            new Range("report.range.4", 4),
            new Range("report.range.8", 8),
            new Range("report.range.12", 12),
            new Range("report.range.all", TimeReport.ALL));

    private final BoardService service;
    private final I18n i18n;
    private final Stage stage;
    private final java.util.List<String> stylesheets;

    private final Label subtitle = new Label();
    private final ComboBox<Range> rangePicker = new ComboBox<>();
    private final TableView<TimeReport.ProcessRow> processTable = new TableView<>();
    private final TableView<TimeReport.WeekRow> weekTable = new TableView<>();
    private final Label totalLabel = new Label();

    private TimeReport report;

    private TimeReportWindow(BoardService service, I18n i18n, Stage stage,
                             java.util.List<String> stylesheets) {
        this.service = service;
        this.i18n = i18n;
        this.stage = stage;
        this.stylesheets = stylesheets;
    }

    /** Opens the report window, or brings it to the front when already open. */
    static void show(BoardService service, I18n i18n, java.util.List<String> stylesheets) {
        if (openWindow != null) {
            openWindow.toFront();
            openWindow.requestFocus();
            return;
        }
        Stage stage = new Stage();
        openWindow = stage;
        stage.setOnHidden(event -> openWindow = null);
        new TimeReportWindow(service, i18n, stage, stylesheets).build();
    }

    private void build() {
        subtitle.getStyleClass().add("detail-title");

        rangePicker.setItems(FXCollections.observableArrayList(RANGES));
        rangePicker.getSelectionModel().selectFirst();
        rangePicker.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Range value) {
                return value == null ? "" : i18n.text(value.labelKey());
            }

            @Override
            public Range fromString(String text) {
                return null; // not editable
            }
        });
        rangePicker.setOnAction(event -> refresh());

        Button refresh = new Button("\u21BB");
        refresh.getStyleClass().add("tool-button");
        refresh.setTooltip(new javafx.scene.control.Tooltip(i18n.text("report.refresh")));
        refresh.setOnAction(event -> refresh());

        Button exportCsv = new Button(i18n.text("report.export.csv"));
        exportCsv.setOnAction(event -> export(Format.CSV));
        Button exportPdf = new Button(i18n.text("report.export.pdf"));
        exportPdf.setOnAction(event -> export(Format.PDF));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, subtitle, spacer, rangePicker, refresh, exportCsv, exportPdf);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10));
        header.getStyleClass().add("toolbar");

        buildProcessTable();
        buildWeekTable();

        SplitPane tables = new SplitPane(section("report.by.process", processTable),
                section("report.by.week", weekTable));
        tables.setOrientation(javafx.geometry.Orientation.VERTICAL);

        totalLabel.getStyleClass().add("detail-title");
        HBox footer = new HBox(totalLabel);
        footer.setPadding(new Insets(8, 10, 10, 10));

        BorderPane layout = new BorderPane(tables);
        layout.setTop(header);
        layout.setBottom(footer);
        layout.getStyleClass().add("detail-window");

        Scene scene = new Scene(layout, 620, 620);
        scene.getStylesheets().addAll(stylesheets);
        stage.setScene(scene);
        stage.setTitle(i18n.text("report.title"));
        refresh();
        stage.show();
    }

    private VBox section(String titleKey, TableView<?> table) {
        Label title = new Label(i18n.text(titleKey));
        title.getStyleClass().add("detail-title");
        VBox box = new VBox(4, title, table);
        box.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void buildProcessTable() {
        TableColumn<TimeReport.ProcessRow, String> process = new TableColumn<>(
                i18n.text("report.col.process"));
        process.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.processName(cell.getValue(), i18n)));
        process.setPrefWidth(240);
        TableColumn<TimeReport.ProcessRow, String> cards = new TableColumn<>(
                i18n.text("report.col.cards"));
        cards.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.valueOf(cell.getValue().cardCount())));
        cards.setPrefWidth(90);
        TableColumn<TimeReport.ProcessRow, String> hours = new TableColumn<>(
                i18n.text("report.col.hours"));
        hours.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.hours(cell.getValue().millis())));
        hours.setPrefWidth(100);
        TableColumn<TimeReport.ProcessRow, String> minutes = new TableColumn<>(
                i18n.text("report.col.minutes"));
        minutes.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.minutes(cell.getValue().millis())));
        minutes.setPrefWidth(100);
        processTable.getColumns().setAll(java.util.List.of(process, cards, hours, minutes));
        processTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        processTable.setPlaceholder(new Label(i18n.text("report.empty")));
    }

    private void buildWeekTable() {
        TableColumn<TimeReport.WeekRow, String> week = new TableColumn<>(
                i18n.text("report.col.week"));
        week.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.weekLabel(cell.getValue().weekStart(), i18n.locale())));
        week.setPrefWidth(240);
        TableColumn<TimeReport.WeekRow, String> hours = new TableColumn<>(
                i18n.text("report.col.hours"));
        hours.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.hours(cell.getValue().millis())));
        hours.setPrefWidth(100);
        TableColumn<TimeReport.WeekRow, String> minutes = new TableColumn<>(
                i18n.text("report.col.minutes"));
        minutes.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                TimeReportExport.minutes(cell.getValue().millis())));
        minutes.setPrefWidth(100);
        weekTable.getColumns().setAll(java.util.List.of(week, hours, minutes));
        weekTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        weekTable.setPlaceholder(new Label(i18n.text("report.empty")));
    }

    /** Recomputes the report for the board on screen and the picked range. */
    private void refresh() {
        Range selected = rangePicker.getValue() == null ? RANGES.get(0) : rangePicker.getValue();
        String boardName = boardName();
        subtitle.setText(i18n.text("report.title") + " — " + boardName);
        report = TimeReport.of(service.board(), Instant.now(), ZoneId.systemDefault(),
                selected.weeks());
        processTable.setItems(FXCollections.observableArrayList(report.byProcess()));
        weekTable.setItems(FXCollections.observableArrayList(report.byWeek()));
        totalLabel.setText(report.isEmpty()
                ? i18n.text("report.empty")
                : i18n.text("report.total") + ": " + TimeReportExport.hours(report.total())
                        + " (" + TimeReportExport.minutes(report.total()) + " "
                        + i18n.text("report.col.minutes") + ")");
    }

    private String boardName() {
        var activeId = service.activeBoardId();
        return service.boards().stream()
                .filter(board -> board.id().equals(activeId))
                .map(board -> board.name())
                .findFirst()
                .orElse("");
    }

    private enum Format {
        CSV("csv", "report.export.csv"),
        PDF("pdf", "report.export.pdf");

        private final String extension;
        private final String titleKey;

        Format(String extension, String titleKey) {
            this.extension = extension;
            this.titleKey = titleKey;
        }
    }

    /** Writes the current report to the file the user picks. */
    private void export(Format format) {
        if (report == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text(format.titleKey));
        chooser.setInitialFileName("informe-tiempo." + format.extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                format.extension.toUpperCase(java.util.Locale.ROOT) + " (*." + format.extension + ")",
                "*." + format.extension));
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Path target = file.toPath();
            if (format == Format.CSV) {
                // The BOM is what makes Excel open the UTF-8 file correctly.
                Files.writeString(target, "\uFEFF" + TimeReportExport.csv(report, boardName(), i18n),
                        StandardCharsets.UTF_8);
            } else {
                CardPdfWriter.write(TimeReportExport.text(report, boardName(), i18n, i18n.locale()),
                        target);
            }
        } catch (IOException | RuntimeException failure) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    i18n.text("report.export.failed", String.valueOf(failure.getMessage())));
            alert.initOwner(stage);
            alert.showAndWait();
        }
    }
}
