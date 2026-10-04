package com.personalkanban.domain.board;

import java.util.ArrayList;
import java.util.List;

/** Constants and validation rules for card/process labels. */
public final class LabelConventions {

    private LabelConventions() {
    }

    /** Leading "#" is not allowed: labels are simple names (processes are
     * stored in the dedicated {"code ""#compras"} table). */
    public static boolean startsWithHash(String label) {
        return label != null && label.startsWith("#");
    }

    /** A label must not be empty or blank, and must not start with "#". */
    public static boolean isValid(String label) {
        if (label == null || label.isBlank()) {
            return false;
        }
        String trimmed = label.strip();
        return !trimmed.isEmpty() && !trimmed.startsWith("#") && !containsForbiddenChars(trimmed);
    }

    private static boolean containsForbiddenChars(String label) {
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch <= '\u001F' || ch == '\u007F' || ch == ',' || ch == ';' || ch == '#') {
                return true;
            }
        }
        return false;
    }

    /*     * Splits user input (spaces, commas or semicolons) into a list of valid
     * labels. Input such as "et1 et2 et3", "et1,et2,et3" or
     * "et1,   et2  et3" yields three labels. Invalid tokens (blank, containing
     * a '#', etc.) are dropped, so a manually typed "#compras" is rejected. */
    public static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[\\s,;]+");
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            String cleaned = part.strip();
            if (!cleaned.isEmpty() && isValid(cleaned)) {
                labels.add(cleaned);
            }
        }
        return labels;
    }
}
