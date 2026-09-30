package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.CyclicDependencyException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure policy for card precedence links (session 4.6). The user relates
 * cards like a simple Gantt: which tasks come BEFORE a card and which come
 * AFTER. The only invariant worth enforcing in the domain is acyclicity —
 * "A before B and B before A" is meaningless and would corrupt any future
 * suggested ordering.
 */
public final class DependencyGuard {

    private DependencyGuard() {
    }

    /**
     * Validates one prospective link {@code from → to} ("from precedes to")
     * against the existing links of the board. Rejects self-links, links
     * between unknown cards, duplicates, and anything that would close a
     * cycle (including the reverse link). Returns normally when the link is
     * acceptable; throws {@link CyclicDependencyException} otherwise.
     */
    public static void requireLinkable(Board board, CardId from, CardId to) {
        if (from.equals(to)) {
            throw new IllegalArgumentException("A card cannot precede itself");
        }
        if (board.findCard(from).isEmpty() || board.findCard(to).isEmpty()) {
            throw new IllegalArgumentException("Unknown card in link");
        }
        if (board.outgoingSuccessorsOf(from).contains(to)) {
            return; // duplicate link: idempotent, acceptable
        }
        if (board.outgoingSuccessorsOf(to).contains(from)) {
            throw new CyclicDependencyException("A card cannot both precede and follow the same card");
        }
        // Adding from→to closes a cycle iff `to` already (transitively)
        // precedes `from`.
        if (reaches(board, to, from)) {
            throw new CyclicDependencyException(
                    "This link would create a circular dependency between tasks");
        }
    }

    /**
     * Suggested execution order (topological sort) of the given cards under
     * the precedence links, keeping ties in the caller's stable input order.
     * Detects an unsortable remainder: {@link Result#cycleRemaining()} holds
     * the cards that participate in (or depend on) a cycle.
     */
    public static Result topologicalOrder(Board board, List<CardId> cards) {
        Map<CardId, Integer> indegree = new LinkedHashMap<>();
        for (CardId cardId : cards) {
            indegree.putIfAbsent(cardId, 0);
        }
        for (CardId cardId : cards) {
            for (CardId successor : board.outgoingSuccessorsOf(cardId)) {
                if (indegree.containsKey(successor)) {
                    indegree.merge(successor, 1, Integer::sum);
                }
            }
        }
        Deque<CardId> ready = new ArrayDeque<>();
        indegree.forEach((cardId, degree) -> {
            if (degree == 0) {
                ready.add(cardId);
            }
        });
        List<CardId> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            CardId cardId = ready.poll();
            ordered.add(cardId);
            for (CardId successor : board.outgoingSuccessorsOf(cardId)) {
                Integer degree = indegree.get(successor);
                if (degree == null) {
                    continue;
                }
                if (degree - 1 == 0) {
                    ready.add(successor);
                }
                indegree.put(successor, degree - 1);
            }
        }
        List<CardId> remaining = indegree.keySet().stream()
                .filter(cardId -> !ordered.contains(cardId))
                .toList();
        return new Result(List.copyOf(ordered), List.copyOf(remaining));
    }

    /** True when {@code start} transitively reaches {@code target} via links. */
    private static boolean reaches(Board board, CardId start, CardId target) {
        Set<CardId> visited = new LinkedHashSet<>();
        Deque<CardId> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            CardId current = pending.poll();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                pending.addAll(board.outgoingSuccessorsOf(current));
            }
        }
        return false;
    }

    /** Outcome of a suggested ordering attempt. */
    public record Result(List<CardId> ordered, List<CardId> cycleRemaining) {
    }
}
