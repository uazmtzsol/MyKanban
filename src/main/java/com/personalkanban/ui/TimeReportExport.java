package com.personalkanban.ui;

import com.personalkanban.application.TimeReport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * Renders a {@link TimeReport} as CSV or as plain text (session 14, B1).
 * Pure Java: no JavaFX, no file IO, so both renderings are unit-testable and
 * the PDF export can reuse the text one through
 * {@link com.personalkanban.ui.pdf.CardPdfWriter#write(String, java.nio.file.Path)}.
 *
 * <p>The CSV uses a semicolon, the separator Excel expects in the Spanish
 * locale, and ISO dates so the file can be sorted and re-processed; the text
 * rendering is for humans and uses localized week labels.</p>
 */
final class TimeReportExport {

    private TimeReportExport() {
    }

    /** {@code "12:05"} — whole hours (never wrapped at 24 h) and two-digit minutes. */
    static String hours(long millis) {
        long totalMinutes = Math.max(0, millis) / 60000L;
        return String.format("%d:%02d", totalMinutes / 60, totalMinutes % 60);
    }

    /** Whole minutes, so a spreadsheet can add and average the numbers. */
    static String minutes(long millis) {
        return String.valueOf(Math.max(0, millis) / 60000L);
    }

    /** Name of a process row; {@code null} is the "no process" remainder. */
    static String processName(TimeReport.ProcessRow row, I18n i18n) {
        return row.name() == null ? i18n.text("report.no.process") : row.name();
    }

    /** Localized week label with its ISO week number, e.g. {@code "12 oct 2026 (W42)"}. */
    static String weekLabel(LocalDate weekStart, Locale locale) {
        String date = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(weekStart);
        int week = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return date + " (W" + week + ")";
    }

    /** CSV of the whole report: header, per-process table, per-week table. */
    static String csv(TimeReport report, String boardName, I18n i18n) {
        StringBuilder out = new StringBuilder();
        out.append(field(i18n.text("report.title"))).append(';')
                .append(field(i18n.text("report.board"))).append(';')
                .append(field(boardName)).append('\n');
        out.append(field(i18n.text("report.from"))).append(';')
                .append(report.firstWeek()).append('\n');
        out.append(field(i18n.text("report.to"))).append(';')
                .append(report.lastWeek()).append('\n');
        out.append(field(i18n.text("report.total"))).append(';')
                .append(hours(report.total())).append(';')
                .append(minutes(report.total())).append('\n');
        out.append('\n');

        out.append(field(i18n.text("report.col.process"))).append(';')
                .append(field(i18n.text("report.col.cards"))).append(';')
                .append(field(i18n.text("report.col.hours"))).append(';')
                .append(field(i18n.text("report.col.minutes"))).append('\n');
        for (TimeReport.ProcessRow row : report.byProcess()) {
            out.append(field(processName(row, i18n))).append(';')
                    .append(row.cardCount()).append(';')
                    .append(hours(row.millis())).append(';')
                    .append(minutes(row.millis())).append('\n');
        }
        out.append('\n');

        out.append(field(i18n.text("report.col.week"))).append(';')
                .append(field(i18n.text("report.col.hours"))).append(';')
                .append(field(i18n.text("report.col.minutes"))).append('\n');
        for (TimeReport.WeekRow row : report.byWeek()) {
            out.append(row.weekStart()).append(';')
                    .append(hours(row.millis())).append(';')
                    .append(minutes(row.millis())).append('\n');
        }
        return out.toString();
    }

    /** Plain-text rendering (the first line is the PDF title). */
    static String text(TimeReport report, String boardName, I18n i18n, Locale locale) {
        StringBuilder out = new StringBuilder();
        out.append(i18n.text("report.title")).append(" — ").append(boardName).append('\n');
        out.append(i18n.text("report.from")).append(' ').append(report.firstWeek())
                .append("   ").append(i18n.text("report.to")).append(' ')
                .append(report.lastWeek()).append('\n');
        if (report.isEmpty()) {
            out.append('\n').append(i18n.text("report.empty")).append('\n');
            return out.toString();
        }
        out.append(i18n.text("report.total")).append(": ")
                .append(hours(report.total())).append(" (").append(minutes(report.total()))
                .append(' ').append(i18n.text("report.col.minutes")).append(")\n");

        out.append('\n').append(i18n.text("report.by.process")).append('\n');
        for (TimeReport.ProcessRow row : report.byProcess()) {
            out.append("  ").append(processName(row, i18n))
                    .append(" — ").append(hours(row.millis()))
                    .append(" (").append(row.cardCount()).append(' ')
                    .append(i18n.text("report.col.cards")).append(")\n");
        }

        out.append('\n').append(i18n.text("report.by.week")).append('\n');
        for (TimeReport.WeekRow row : report.byWeek()) {
            out.append("  ").append(weekLabel(row.weekStart(), locale))
                    .append(" — ").append(hours(row.millis())).append('\n');
        }
        return out.toString();
    }

    /** One CSV field: quoted only when it has to be. */
    private static String field(String value) {
        String text = value == null ? "" : value;
        boolean needsQuotes = text.indexOf(';') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        if (!needsQuotes) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
