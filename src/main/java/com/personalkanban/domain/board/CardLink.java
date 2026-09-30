package com.personalkanban.domain.board;

/**
 * One precedence link as stored in snapshots: {@code from} precedes
 * {@code to} (session 4.6). A flat pair list instead of a nested map keeps
 * the JSON shape trivially (de)serializable (map keys would serialize as
 * {@code CardId[value=…]} strings) and preserves insertion order.
 */
public record CardLink(CardId from, CardId to) {

    public CardLink {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Card link endpoints must not be null");
        }
    }
}
