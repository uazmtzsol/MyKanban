package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Links two cards: {@code from} precedes {@code to} (session 4.6). */
public final class LinkCardsCommand implements BoardCommand {

    private final CardId from;
    private final CardId to;

    public LinkCardsCommand(CardId from, CardId to) {
        this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to);
    }

    @Override
    public void execute(Board board) {
        board.linkCards(from, to);
    }
}
