package com.personalkanban.application;

import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.ChecklistItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Search across every board of the catalogue (session 14, user request B2).
 *
 * <p>The per-board full-text match already existed
 * ({@link BoardService#findCards(BoardId, String)}); what was missing was a
 * single answer for the whole catalogue. This class walks the boards — the
 * active one first, because that is where the user believes they are — and
 * asks each of them, so no board ever becomes the active one just to be
 * searched.</p>
 *
 * <p>Each hit carries <em>where</em> it matched (title, description, notes,
 * label or checklist item) and a short excerpt around the match, which is what
 * turns a list of titles into something you can recognize without opening the
 * card. Hits are capped per board: one huge board must not drown the others,
 * and the result says whether that cap was hit.</p>
 */
public final class GlobalSearch {

    /** How many hits each board may contribute by default. */
    public static final int DEFAULT_LIMIT_PER_BOARD = 20;

    /** Characters of context kept on each side of the match. */
    private static final int EXCERPT_CONTEXT = 30;

    /** Field of the card where the query was found (translated by the UI). */
    public enum Field {
        TITLE, DESCRIPTION, NOTES, LABEL, CHECKLIST
    }

    /**
     * One result: the board it lives in, the matching field and an excerpt of
     * the matched text. {@code card} is read-only (it comes from a private
     * aggregate, never from the active board's live model).
     */
    public record Hit(BoardId boardId, String boardName, Field field, String snippet, Card card) {}

    /** All the hits plus whether any board was cut off by the per-board cap. */
    public record Result(List<Hit> hits, boolean truncated) {

        public Result {
            hits = List.copyOf(hits);
        }

        public boolean isEmpty() {
            return hits.isEmpty();
        }
    }

    private final BoardService service;

    public GlobalSearch(BoardService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /** Searches every board with the default per-board cap. */
    public List<Hit> search(String query) {
        return search(query, DEFAULT_LIMIT_PER_BOARD).hits();
    }

    /**
     * Searches every board, keeping at most {@code limitPerBoard} hits each.
     * A blank query matches nothing (an empty box shows an empty list, exactly
     * like the in-board search).
     */
    public Result search(String query, int limitPerBoard) {
        String needle = query == null ? "" : query.strip().toLowerCase();
        if (needle.isEmpty()) {
            return new Result(List.of(), false);
        }
        List<Hit> hits = new ArrayList<>();
        boolean truncated = false;
        for (BoardDescriptor board : boardsActiveFirst()) {
            List<Card> found = service.findCards(board.id(), query);
            truncated = truncated || found.size() > limitPerBoard;
            for (Card card : found.stream().limit(Math.max(0, limitPerBoard)).toList()) {
                hits.add(hit(board, card, needle));
            }
        }
        return new Result(hits, truncated);
    }

    /** The boards of the catalogue, the one on screen first. */
    private List<BoardDescriptor> boardsActiveFirst() {
        List<BoardDescriptor> ordered = new ArrayList<>();
        BoardId active = service.activeBoardId();
        for (BoardDescriptor board : service.boards()) {
            if (board.id().equals(active)) {
                ordered.add(board);
            }
        }
        for (BoardDescriptor board : service.boards()) {
            if (!board.id().equals(active)) {
                ordered.add(board);
            }
        }
        return ordered;
    }

    private static Hit hit(BoardDescriptor board, Card card, String needle) {
        Match match = firstMatch(card, needle);
        return new Hit(board.id(), board.name(), match.field(), match.snippet(), card);
    }

    private record Match(Field field, String snippet) {
    }

    /** First field that matches, in the same order the search itself walks them. */
    private static Match firstMatch(Card card, String needle) {
        for (Field field : List.of(Field.TITLE, Field.DESCRIPTION, Field.NOTES)) {
            Match match = textMatch(field, textOf(card, field), needle);
            if (match != null) {
                return match;
            }
        }
        for (String label : card.labels()) {
            if (contains(label, needle)) {
                return new Match(Field.LABEL, label);
            }
        }
        for (ChecklistItem item : card.checklist()) {
            Match match = textMatch(Field.CHECKLIST, item.text(), needle);
            if (match != null) {
                return match;
            }
        }
        // findCards only returns cards it matched, so this is defensive: show
        // the title rather than an empty row.
        return new Match(Field.TITLE, card.title());
    }

    private static String textOf(Card card, Field field) {
        return switch (field) {
            case TITLE -> card.title();
            case DESCRIPTION -> card.description();
            case NOTES -> card.notes();
            default -> "";
        };
    }

    private static Match textMatch(Field field, String text, String needle) {
        if (text == null) {
            return null;
        }
        int index = text.toLowerCase().indexOf(needle);
        if (index < 0) {
            return null;
        }
        return new Match(field, excerpt(text, index, needle.length()));
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase().contains(needle);
    }

    /** A readable window around the match, single-line and ellipsis-trimmed. */
    static String excerpt(String text, int index, int length) {
        int from = Math.max(0, index - EXCERPT_CONTEXT);
        int to = Math.min(text.length(), index + length + EXCERPT_CONTEXT);
        String slice = text.substring(from, to).replace('\n', ' ').replace('\r', ' ').strip();
        return (from > 0 ? "…" : "") + slice + (to < text.length() ? "…" : "");
    }
}
