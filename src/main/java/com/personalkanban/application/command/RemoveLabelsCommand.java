package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.List;

/** Bulk: removes labels from several cards (case-insensitive match). */
public final class RemoveLabelsCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final List<String> labels;

    public RemoveLabelsCommand(List<CardId> cardIds, List<String> labels) {
        this.cardIds = List.copyOf(cardIds);
        this.labels = List.copyOf(labels);
    }

    @Override
    public void execute(Board board) {
        board.removeLabels(cardIds, labels);
    }
}
