package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Removes the precedence link between two cards (session 4.6). */
public final class UnlinkCardsCommand implements BoardCommand {

    private final CardId from;
    private final CardId to;

    public UnlinkCardsCommand(CardId from, CardId to) {
        this.from = Objects.requireNonNull(from);
        this.to = Objects.requireNonNull(to);
    }

    @Override
    public void execute(Board board) {
        board.unlinkCards(from, to);
    }
}
