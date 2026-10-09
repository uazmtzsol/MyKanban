package com.personalkanban.ui;

import com.personalkanban.domain.board.CardId;

import java.util.List;

/**
 * Keyboard-navigation policy for the kanban view (session 10, user request):
 * which card a key should focus, given the visible cards of every column.
 *
 * <p>Coordinates are <em>column-major</em>: {@code columns.get(c).get(i)} is
 * the i-th visible card of column c, in display order. The controller feeds
 * only the cards that pass the active filters, so the keys never land on a
 * hidden card. Pure Java on purpose — no JavaFX — so every wrap-around rule
 * is verifiable with plain unit tests.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>{@link #stepInColumn} — Down/Up move inside the current column and
 *       wrap (last → first, first → last). Without a focused card, Down
 *       starts at the first card of the board and Up at the last one.</li>
 *   <li>{@link #neighbouringColumnFirst} — Alt+Left/Right jumps to the first
 *       card of the neighbouring column, wrapping around the board and
 *       skipping empty columns (so the key never appears dead).</li>
 *   <li>{@link #cardAtPosition} — digit keys select the n-th card of the
 *       current column (no focused card = first column).</li>
 * </ul>
 */
final class CardNavigator {

    private CardNavigator() {
    }

    /** First visible card of the board, or null when nothing is visible. */
    static CardId firstCard(List<List<CardId>> columns) {
        for (List<CardId> column : columns) {
            if (!column.isEmpty()) {
                return column.getFirst();
            }
        }
        return null;
    }

    /** Last visible card of the board, or null when nothing is visible. */
    static CardId lastCard(List<List<CardId>> columns) {
        for (int c = columns.size() - 1; c >= 0; c--) {
            List<CardId> column = columns.get(c);
            if (!column.isEmpty()) {
                return column.getLast();
            }
        }
        return null;
    }

    /** Index of the column holding {@code focused}, or -1 (no focus / not visible). */
    static int columnIndexOf(List<List<CardId>> columns, CardId focused) {
        if (focused == null) {
            return -1;
        }
        for (int c = 0; c < columns.size(); c++) {
            if (columns.get(c).contains(focused)) {
                return c;
            }
        }
        return -1;
    }

    /**
     * Down ({@code direction > 0}) / Up ({@code direction < 0}) inside the
     * column of the focused card, wrapping at both ends.
     */
    static CardId stepInColumn(List<List<CardId>> columns, CardId focused, int direction) {
        int step = direction < 0 ? -1 : 1;
        int column = columnIndexOf(columns, focused);
        if (column < 0) {
            return step > 0 ? firstCard(columns) : lastCard(columns);
        }
        List<CardId> cards = columns.get(column);
        int index = cards.indexOf(focused);
        return cards.get((index + step + cards.size()) % cards.size());
    }

    /**
     * First card of the neighbouring column in {@code direction} (+1 = right,
     * -1 = left), wrapping around the board and skipping empty columns.
     * Returns null when no other column holds a visible card.
     */
    static CardId neighbouringColumnFirst(List<List<CardId>> columns, CardId focused, int direction) {
        if (columns.isEmpty()) {
            return null;
        }
        int step = direction < 0 ? -1 : 1;
        int start = columnIndexOf(columns, focused);
        if (start < 0) {
            return firstCard(columns);
        }
        int size = columns.size();
        for (int offset = 1; offset <= size; offset++) {
            List<CardId> column = columns.get(((start + step * offset) % size + size) % size);
            if (!column.isEmpty()) {
                return column.getFirst();
            }
        }
        return null;
    }

    /**
     * The card at zero-based {@code position} in the current column; without
     * a focused card the first column is the "current" one. Returns null when
     * the column holds fewer cards.
     */
    static CardId cardAtPosition(List<List<CardId>> columns, CardId focused, int position) {
        if (position < 0) {
            return null;
        }
        int column = columnIndexOf(columns, focused);
        if (column < 0) {
            column = firstNonEmptyIndex(columns);
        }
        if (column < 0) {
            return null;
        }
        List<CardId> cards = columns.get(column);
        return position < cards.size() ? cards.get(position) : null;
    }

    private static int firstNonEmptyIndex(List<List<CardId>> columns) {
        for (int c = 0; c < columns.size(); c++) {
            if (!columns.get(c).isEmpty()) {
                return c;
            }
        }
        return -1;
    }
}
