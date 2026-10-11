package com.personalkanban.domain.board;

import java.util.ArrayList;
import java.util.List;

/**
 * The titled notes (or comments) of a card, parsed from the card's single
 * notes text so the storage schema and its undo history stay untouched.
 *
 * <p>Format: one note per {@code "%% "} marker line (a plain-text marker
 * that is <em>not</em> valid Markdown, so pasted Markdown can never fragment
 * the list), the body being everything up to the next marker:</p>
 *
 * <pre>
 * %% Compras
 *
 * Leche, pan
 *
 * %% Recordatorios
 *
 * Llamar al fontanero
 * </pre>
 *
 * <p>Text without any marker — notes written before this feature — parses as
 * a single untitled note, so nothing is lost: it is rewritten in the current
 * format the first time the user saves from the notes list. For backwards
 * compatibility, text whose <em>first</em> line starts with the old
 * {@code "## "} heading (the original session-11 serialization) still parses
 * with that legacy separator, so previously structured multi-notes keep their
 * entries. Known limitation of that legacy path: a pasted Markdown document
 * that <em>starts</em> with a level-2 heading is read as legacy structure.</p>
 */
public record CardNotes(List<Note> notes) {

    /** One titled note; an empty title is rendered as "(untitled)" by the UI. */
    public record Note(String title, String body) {

        public Note {
            title = title == null ? "" : title.strip();
            body = body == null ? "" : body.strip();
        }

        public boolean isBlank() {
            return title.isEmpty() && body.isEmpty();
        }
    }

    /** Marker that separates notes in the stored text (not valid Markdown). */
    public static final String HEADING = "%% ";

    /** Original session-11 separator; still parsed when the text starts with it. */
    public static final String LEGACY_HEADING = "## ";

    public CardNotes {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    public static CardNotes empty() {
        return new CardNotes(List.of());
    }

    /** Parses the raw notes text; blank text yields no notes. */
    public static CardNotes parse(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        String stripped = text.strip();
        // Legacy texts (written before the marker stopped being Markdown)
        // start with a "## " heading; they keep parsing with that separator
        // so their note structure survives. Everything else is one note
        // body: its Markdown headings no longer fragment the list.
        String separator = startsWithLegacyHeading(stripped) ? LEGACY_HEADING : HEADING;
        List<Note> parsed = new ArrayList<>();
        String currentTitle = null;
        StringBuilder body = new StringBuilder();
        for (String line : stripped.split("\\R", -1)) {
            if (line.startsWith(separator)) {
                if (currentTitle != null) {
                    parsed.add(new Note(currentTitle, body.toString()));
                } else if (!body.toString().isBlank()) {
                    parsed.add(new Note("", body.toString()));
                }
                currentTitle = line.substring(separator.length()).strip();
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        Note last = new Note(currentTitle == null ? "" : currentTitle, body.toString());
        if (!last.isBlank()) {
            parsed.add(last);
        }
        return new CardNotes(parsed);
    }

    /** True when the very first line carries the legacy "## " heading. */
    private static boolean startsWithLegacyHeading(String text) {
        int lineEnd = text.indexOf('\n');
        String firstLine = lineEnd < 0 ? text : text.substring(0, lineEnd);
        return firstLine.startsWith(LEGACY_HEADING);
    }

    /** Serializes back to the storage format (empty text when there are none). */
    public String serialize() {
        if (notes.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Note note : notes) {
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(HEADING).append(note.title()).append('\n');
            if (!note.body().isEmpty()) {
                out.append('\n').append(note.body()).append('\n');
            }
        }
        return out.toString().strip();
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    public int size() {
        return notes.size();
    }

    /** Adds a note at the end, ignoring a completely blank one. */
    public CardNotes withNote(String title, String body) {
        Note note = new Note(title, body);
        if (note.isBlank()) {
            return this;
        }
        List<Note> copy = new ArrayList<>(notes);
        copy.add(note);
        return new CardNotes(copy);
    }

    /** Replaces a note in place; an unknown index leaves the model unchanged. */
    public CardNotes replaced(int index, String title, String body) {
        if (index < 0 || index >= notes.size()) {
            return this;
        }
        Note note = new Note(title, body);
        if (note.isBlank()) {
            return removed(index);
        }
        List<Note> copy = new ArrayList<>(notes);
        copy.set(index, note);
        return new CardNotes(copy);
    }

    /** Removes a note; an unknown index leaves the model unchanged. */
    public CardNotes removed(int index) {
        if (index < 0 || index >= notes.size()) {
            return this;
        }
        List<Note> copy = new ArrayList<>(notes);
        copy.remove(index);
        return new CardNotes(copy);
    }
}
