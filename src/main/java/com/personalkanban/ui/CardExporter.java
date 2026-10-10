package com.personalkanban.ui;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardNotes;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.TimelineEntry;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders one card to a portable document (session 10, F3): plain text or
 * markdown, with only the sections the user picked and — for the notes —
 * only the notes chosen (all of them by default). Pure Java: no JavaFX, no
 * file IO, no locale surprises beyond the labels the caller passes in via
 * {@link I18n}, so the content rules are unit-testable.
 *
 * <p>The PDF variant writes exactly this plain-text rendering through
 * {@code CardPdfWriter}; the dialog and the file chooser live in
 * {@link Dialogs} / {@link BoardController}.</p>
 */
final class CardExporter {

    /** Output format chosen in the export dialog. */
    enum Format {
        TXT, MARKDOWN, PDF;

        String fileExtension() {
            return switch (this) {
                case TXT -> ".txt";
                case MARKDOWN -> ".md";
                case PDF -> ".pdf";
            };
        }
    }

    /** Which sections the document must contain. */
    record Sections(boolean data, boolean checklist, boolean notes, boolean time) {

        static Sections all() {
            return new Sections(true, true, true, true);
        }

        boolean any() {
            return data || checklist || notes || time;
        }
    }

    /**
     * The dialog's answer: sections, indexes of the notes to export (into
     * {@link CardNotes#parse} order — empty means "no note") and the format.
     */
    record ExportForm(Sections sections, Set<Integer> noteIndexes, Format format) {
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CardExporter() {
    }

    /**
     * Builds the document. The result always ends with exactly one newline
     * (or is a single newline when nothing was selected).
     */
    static String render(Card card, List<TimelineEntry> timeEntries, ExportForm form, I18n i18n) {
        boolean markdown = form.format() == Format.MARKDOWN;
        Sections sections = form.sections();
        StringBuilder out = new StringBuilder();

        if (sections.data()) {
            appendTitle(out, card.title(), markdown);
            if (!card.description().isBlank()) {
                if (markdown) {
                    out.append("## ").append(i18n.text("export.field.description")).append('\n');
                } else {
                    out.append(i18n.text("export.field.description")).append(":\n");
                }
                out.append(card.description().strip()).append("\n\n");
            }
            if (card.dueDate() != null) {
                out.append(i18n.text("export.field.due")).append(": ")
                        .append(card.dueDate()).append('\n');
            }
            if (!card.labels().isEmpty()) {
                out.append(i18n.text("export.field.labels")).append(": ")
                        .append(String.join(", ", card.labels())).append('\n');
            }
            if (card.dueDate() != null || !card.labels().isEmpty()) {
                out.append('\n');
            }
        }

        if (sections.checklist() && !card.checklist().isEmpty()) {
            appendHeading(out, i18n.text("export.section.checklist"), markdown);
            for (ChecklistItem item : card.checklist()) {
                out.append("- [").append(item.done() ? 'x' : ' ').append("] ")
                        .append(item.text()).append('\n');
            }
            out.append('\n');
        }

        if (sections.notes()) {
            CardNotes parsed = CardNotes.parse(card.notes());
            boolean headingWritten = false;
            for (int index : form.noteIndexes().stream().sorted().toList()) {
                if (index < 0 || index >= parsed.size()) {
                    continue;
                }
                CardNotes.Note note = parsed.notes().get(index);
                if (!headingWritten) {
                    appendHeading(out, i18n.text("export.section.notes"), markdown);
                    headingWritten = true;
                }
                String title = note.title().isEmpty()
                        ? i18n.text("card.notes.untitled") : note.title();
                if (markdown) {
                    out.append("### ").append(title).append('\n');
                } else {
                    out.append(title).append('\n');
                }
                if (!note.body().isEmpty()) {
                    out.append(note.body().strip()).append('\n');
                }
                out.append('\n');
            }
        }

        if (sections.time() && timeEntries != null && !timeEntries.isEmpty()) {
            appendHeading(out, i18n.text("export.section.time"), markdown);
            for (TimelineEntry entry : timeEntries) {
                out.append("- ").append(formatInstant(entry.start())).append(" \u2013 ")
                        .append(entry.end() == null
                                ? i18n.text("export.time.running")
                                : formatInstant(entry.end()));
                if (entry.hasComment()) {
                    out.append(" : ").append(entry.comment());
                }
                out.append('\n');
            }
            out.append('\n');
        }

        return out.toString().stripTrailing() + "\n";
    }

    private static void appendTitle(StringBuilder out, String title, boolean markdown) {
        String clean = title == null ? "" : title.strip();
        if (markdown) {
            out.append("# ").append(clean).append("\n\n");
        } else {
            out.append(clean).append('\n');
            out.append("=".repeat(Math.min(Math.max(clean.length(), 3), 60))).append("\n\n");
        }
    }

    private static void appendHeading(StringBuilder out, String label, boolean markdown) {
        if (markdown) {
            out.append("## ").append(label).append('\n');
        } else {
            out.append(label.toUpperCase(Locale.ROOT)).append('\n');
        }
    }

    private static String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        return TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }
}
