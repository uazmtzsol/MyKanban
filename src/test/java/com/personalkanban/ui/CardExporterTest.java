package com.personalkanban.ui;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardAdded;
import com.personalkanban.domain.board.CardNotes;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (F3): what a card export contains — sections on/off, notes
 * selectable (all by default), txt vs markdown, and the time-record shape
 * {@code start – end : note} the user asked for.
 */
class CardExporterTest {

    private final I18n i18n = new I18n(Locale.of("en"));

    private final Board board = new Board(new BoardId("b1"));

    /** A card with every section populated: description, due date, labels,
     *  a done and a pending checklist item, two titled notes. */
    private com.personalkanban.domain.board.Card richCard() {
        var columnId = board.addColumn("Todo", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, "Buy a gift",
                "First line\nSecond line",
                BoardColor.DEFAULT,
                LocalDate.of(2026, 10, 9),
                List.of("home", "Importante"),
                "## Shopping\n\nMilk, bread\n\n## Reminders\n\nCall the plumber",
                null, null);
        var created = board.findCard(added.cardId()).orElseThrow();
        ChecklistItem done = board.addChecklistItem(created.id(), "done item");
        board.setChecklistItemDone(created.id(), done.id(), true);
        board.addChecklistItem(created.id(), "pending item");
        return board.findCard(added.cardId()).orElseThrow();
    }

    private static List<TimelineEntry> timeEntries(com.personalkanban.domain.board.Card card) {
        Instant start = LocalDateTime.of(2026, 10, 9, 10, 0)
                .atZone(ZoneId.systemDefault()).toInstant();
        Instant end = LocalDateTime.of(2026, 10, 9, 11, 30)
                .atZone(ZoneId.systemDefault()).toInstant();
        return List.of(TimelineEntry.restore(card.id(), EntryId.newId(), start, end, "worked"),
                TimelineEntry.restore(card.id(), EntryId.newId(), start, null, "still on it"));
    }

    private String render(CardExporter.Sections sections, Set<Integer> notes,
                          CardExporter.Format format) {
        com.personalkanban.domain.board.Card c = richCard();
        return CardExporter.render(c, timeEntries(c),
                new CardExporter.ExportForm(sections, notes, format), i18n);
    }

    // ------------------------------------------------------------------ content

    @Test
    void txtExportContainsEveryRequestedSection() {
        String out = render(CardExporter.Sections.all(), Set.of(0, 1), CardExporter.Format.TXT);

        assertThat(out).startsWith("Buy a gift\n" + "=".repeat("Buy a gift".length()));
        assertThat(out).contains("Description:\nFirst line\nSecond line");
        assertThat(out).contains("Due date: 2026-10-09");
        assertThat(out).contains("Labels: home, Importante");
        assertThat(out).contains("- [x] done item");
        assertThat(out).contains("- [ ] pending item");
        assertThat(out).contains("NOTES");
        assertThat(out).contains("Shopping");
        assertThat(out).contains("Reminders");
        assertThat(out).contains("Milk, bread");
        assertThat(out).contains("TIME RECORDS");
        assertThat(out).contains("2026-10-09 10:00 \u2013 2026-10-09 11:30 : worked");
        assertThat(out).contains("2026-10-09 10:00 \u2013 " + i18n.text("export.time.running"));
    }

    @Test
    void everySectionCanBeSwitchedOffIndividually() {
        String onlyChecklist = render(
                new CardExporter.Sections(false, true, false, false),
                Set.of(0, 1), CardExporter.Format.TXT);

        assertThat(onlyChecklist).contains("- [x] done item");
        assertThat(onlyChecklist).doesNotContain("Buy a gift");
        assertThat(onlyChecklist).doesNotContain("Due date");
        assertThat(onlyChecklist).doesNotContain("NOTES");
        assertThat(onlyChecklist).doesNotContain("TIME RECORDS");

        String onlyData = render(
                new CardExporter.Sections(true, false, false, false),
                Set.of(0, 1), CardExporter.Format.TXT);

        assertThat(onlyData).contains("Due date: 2026-10-09");
        assertThat(onlyData).doesNotContain("done item");
        assertThat(onlyData).doesNotContain("NOTES");
    }

    @Test
    void onlyTheSelectedNotesAreExported() {
        String oneNote = render(CardExporter.Sections.all(), Set.of(1), CardExporter.Format.TXT);

        assertThat(oneNote).contains("Reminders");
        assertThat(oneNote).doesNotContain("Shopping");
        assertThat(oneNote).doesNotContain("Milk, bread");

        String noNotes = render(CardExporter.Sections.all(), Set.of(), CardExporter.Format.TXT);
        assertThat(noNotes).doesNotContain("NOTES");
        assertThat(noNotes).doesNotContain("Milk, bread");
    }

    @Test
    void untitledNotesFallBackToTheTranslatedPlaceholder() {
        var columnId = board.addColumn("T", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, "Plain notes", "", BoardColor.DEFAULT,
                null, List.of(), "text written before notes had titles", null, null);
        var plain = board.findCard(added.cardId()).orElseThrow();

        String out = CardExporter.render(plain, List.of(),
                new CardExporter.ExportForm(CardExporter.Sections.all(), Set.of(0),
                        CardExporter.Format.TXT), i18n);

        assertThat(out).contains(i18n.text("card.notes.untitled"));
        assertThat(out).contains("text written before notes had titles");
    }

    // ------------------------------------------------------------------ formats

    @Test
    void markdownUsesHeadingsAndTheSameChecklistMarkers() {
        String out = render(CardExporter.Sections.all(), Set.of(0, 1), CardExporter.Format.MARKDOWN);

        assertThat(out).startsWith("# Buy a gift");
        assertThat(out).contains("## " + i18n.text("export.field.description"));
        assertThat(out).contains("## " + i18n.text("export.section.checklist"));
        assertThat(out).contains("## " + i18n.text("export.section.notes"));
        assertThat(out).contains("### Shopping");
        assertThat(out).contains("- [x] done item");
        assertThat(out).doesNotContain("============");
    }

    @Test
    void pdfFormatRendersThePlainTextVariant() {
        String out = render(CardExporter.Sections.all(), Set.of(0), CardExporter.Format.PDF);

        assertThat(out).startsWith("Buy a gift\n");
        assertThat(out).contains("NOTES");
        assertThat(out).doesNotContain("# Buy a gift");
    }

    @Test
    void theDocumentAlwaysEndsWithExactlyOneNewline() {
        for (CardExporter.Format format : CardExporter.Format.values()) {
            String out = render(CardExporter.Sections.all(), Set.of(0, 1), format);
            assertThat(out).endsWith("\n");
            assertThat(out).doesNotEndWith("\n\n");
        }
    }

    @Test
    void notesParsingMatchesWhatTheDialogOffersTheUser() {
        com.personalkanban.domain.board.Card c = richCard();
        CardNotes parsed = CardNotes.parse(c.notes());
        assertThat(parsed.size()).isEqualTo(2);
        assertThat(parsed.notes().get(0).title()).isEqualTo("Shopping");
        assertThat(parsed.notes().get(1).title()).isEqualTo("Reminders");
    }
}
