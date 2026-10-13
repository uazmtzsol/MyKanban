package com.personalkanban.application;

import com.personalkanban.domain.board.Card;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Quick capture with inline syntax (user request T13 · B4, pure — no JavaFX).
 *
 * <p>One line like {@code "Llamar al banco +casa ! @mañana"} becomes the fields
 * of a new card:</p>
 * <ul>
 *   <li>{@code +etiqueta} adds a label; {@code +urgente} / {@code +importante}
 *       map onto the system labels (same as the quick flags). {@code #} is
 *       <b>not</b> a label marker on purpose: in this app {@code #} identifies
 *       process tags.</li>
 *   <li>{@code !} = urgent, {@code *} = important (standalone tokens).</li>
 *   <li>{@code @hoy}, {@code @mañana}, {@code @+3d} or {@code @2026-10-20}
 *       set the due date. An unparsable date is left in the title instead of
 *       silently dropped.</li>
 *   <li>The remaining words, in order, are the title. Signs inside a word are
 *       never interpreted ({@code C++}, {@code a*b}).</li>
 * </ul>
 */
public final class QuickCapture {

    /** Upper bound of labels a card accepts (mirrors the domain's limit). */
    public static final int MAX_LABELS = 8;

    /** The fields a quick-capture line produces. */
    public record Draft(String title, List<String> labels, LocalDate dueDate) {

        public Draft {
            title = title == null ? "" : title;
            labels = labels == null ? List.of() : List.copyOf(labels);
        }

        /** Whether the line produced a card that can actually be created. */
        public boolean canCreate() {
            return !title.isBlank();
        }
    }

    private QuickCapture() {
    }

    /**
     * Parses one line against a reference day ({@code today} is a parameter so
     * the result is deterministic in tests; a null value falls back to the
     * system clock).
     */
    public static Draft parse(String raw, LocalDate today) {
        LocalDate reference = today == null ? LocalDate.now() : today;
        List<String> titleTokens = new ArrayList<>();
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        LocalDate dueDate = null;

        if (raw != null) {
            for (String token : raw.strip().split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if (token.equals("!")) {
                    addLabel(labels, Card.LABEL_URGENT);
                    continue;
                }
                if (token.equals("*")) {
                    addLabel(labels, Card.LABEL_IMPORTANT);
                    continue;
                }
                if (token.length() > 1 && token.charAt(0) == '+') {
                    String label = token.substring(1).strip();
                    if (!label.isEmpty()) {
                        addLabel(labels, systemLabel(label));
                        continue;
                    }
                }
                if (token.length() > 1 && token.charAt(0) == '@') {
                    LocalDate parsed = parseDate(token.substring(1), reference);
                    if (parsed != null) {
                        dueDate = parsed;
                        continue;
                    }
                }
                titleTokens.add(token);
            }
        }
        return new Draft(String.join(" ", titleTokens).strip(), List.copyOf(labels), dueDate);
    }

    /** Maps {@code +urgente}/{@code +importante} onto the system labels. */
    private static String systemLabel(String name) {
        if (name.equalsIgnoreCase("urgente") || name.equalsIgnoreCase("urgent")) {
            return Card.LABEL_URGENT;
        }
        if (name.equalsIgnoreCase("importante") || name.equalsIgnoreCase("important")) {
            return Card.LABEL_IMPORTANT;
        }
        return name;
    }

    /** Adds a label, skipping case-insensitive duplicates and respecting the cap. */
    private static void addLabel(LinkedHashSet<String> labels, String label) {
        if (labels.size() >= MAX_LABELS) {
            return;
        }
        for (String existing : labels) {
            if (existing.equalsIgnoreCase(label)) {
                return;
            }
        }
        labels.add(label);
    }

    /**
     * Due date of an {@code @token}, or null when it is not a valid date (the
     * caller then leaves the token in the title).
     */
    private static LocalDate parseDate(String value, LocalDate today) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("hoy") || normalized.equals("today")) {
            return today;
        }
        if (normalized.equals("mañana") || normalized.equals("manana")
                || normalized.equals("tomorrow")) {
            return today.plusDays(1);
        }
        if (normalized.startsWith("+") && normalized.endsWith("d")) {
            try {
                int days = Integer.parseInt(normalized.substring(1, normalized.length() - 1));
                return days > 0 ? today.plusDays(days) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
