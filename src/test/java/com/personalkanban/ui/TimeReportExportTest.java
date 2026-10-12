package com.personalkanban.ui;

import com.personalkanban.application.TimeReport;
import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests of the report renderings (session 14, B1): the CSV a spreadsheet
 * opens and the plain text the PDF export writes.
 */
class TimeReportExportTest {

    private static final Instant NOW = at("2026-10-12", "10:00");
    private static final I18n ES = new I18n(Locale.of("es"));

    private static Instant at(String date, String time) {
        return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                .toInstant(ZoneOffset.UTC);
    }

    private static TimeReport report(String processName) {
        Board board = new Board();
        var column = board.addColumn("Todo", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardId inProcess = board.addCard(column, "A", "", BoardColor.DEFAULT).cardId();
        CardId loose = board.addCard(column, "B", "", BoardColor.DEFAULT).cardId();
        var process = board.addProcess(processName);
        board.assignCardToProcess(inProcess, process.id());
        logged(board, inProcess, "08:00", "09:30"); // 90 min
        logged(board, loose, "09:00", "09:30");     // 30 min, no process
        return TimeReport.of(board, NOW, ZoneOffset.UTC, 4);
    }

    private static void logged(Board board, CardId cardId, String from, String to) {
        board.startTimeTracking(cardId, at("2026-10-12", from));
        board.stopTimeTracking(cardId, at("2026-10-12", to));
    }

    @Test
    void hoursNeverWrapAtTwentyFourAndMinutesAreWhole() {
        assertThat(TimeReportExport.hours(0)).isEqualTo("0:00");
        assertThat(TimeReportExport.hours(90 * 60000L)).isEqualTo("1:30");
        assertThat(TimeReportExport.hours(25 * 3600000L)).isEqualTo("25:00");
        assertThat(TimeReportExport.hours(-5)).isEqualTo("0:00"); // defensive
        assertThat(TimeReportExport.minutes(90 * 60000L)).isEqualTo("90");
    }

    @Test
    void csvHasHeaderBothTablesAndTheNoProcessRow() {
        String csv = TimeReportExport.csv(report("Dev"), "Mi tablero", ES);

        assertThat(csv).contains("Informe de tiempo;Tablero;Mi tablero\n");
        assertThat(csv).contains("Desde;2026-09-21\n");
        assertThat(csv).contains("Hasta;2026-10-12\n");
        assertThat(csv).contains("Total;2:00;120\n");
        assertThat(csv).contains("Proceso;Tarjetas;Horas;Minutos\n");
        assertThat(csv).contains("Dev;1;1:30;90\n");
        assertThat(csv).contains("Sin proceso;1;0:30;30\n");
        assertThat(csv).contains("Semana;Horas;Minutos\n");
        assertThat(csv).contains("2026-10-12;2:00;120\n");
    }

    @Test
    void csvQuotesFieldsThatWouldBreakTheColumnLayout() {
        String csv = TimeReportExport.csv(report("A;B"), "Mi tablero", ES);

        assertThat(csv).contains("\"A;B\";1;1:30;90\n");
    }

    @Test
    void textStartsWithTheTitleAndListsBothTables() {
        String text = TimeReportExport.text(report("Dev"), "Mi tablero", ES, Locale.of("es"));

        assertThat(text.lines().findFirst()).hasValue("Informe de tiempo — Mi tablero");
        assertThat(text).contains("Total: 2:00 (120 Minutos)");
        assertThat(text).contains("Tiempo por proceso");
        assertThat(text).contains("Dev — 1:30 (1 Tarjetas)");
        assertThat(text).contains("Sin proceso — 0:30");
        assertThat(text).contains("Tiempo por semana");
    }

    @Test
    void anEmptyReportSaysSoInsteadOfListingZeroes() {
        TimeReport empty = TimeReport.of(new Board(), NOW, ZoneOffset.UTC, 4);

        String text = TimeReportExport.text(empty, "Vacío", ES, Locale.of("es"));
        String csv = TimeReportExport.csv(empty, "Vacío", ES);

        assertThat(text).contains("Nada registrado en este rango");
        assertThat(text).doesNotContain("Tiempo por proceso");
        assertThat(csv).contains("Total;0:00;0\n");
    }

    @Test
    void weekLabelsCarryTheirIsoWeekNumber() {
        assertThat(TimeReportExport.weekLabel(LocalDate.parse("2026-10-12"), Locale.of("es")))
                .isEqualTo("12 oct 2026 (W42)");
    }
}
