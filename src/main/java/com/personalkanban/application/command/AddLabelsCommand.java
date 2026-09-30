package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.List;
import java.util.Objects;

/** Bulk: adds labels to several cards (the UI passes them space/comma-split). */
public final class AddLabelsCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final List<String> labels;

    public AddLabelsCommand(List<CardId> cardIds, List<String> labels) {
        this.cardIds = List.copyOf(cardIds);
        this.labels = List.copyOf(labels);
    }

    @Override
    public void execute(Board board) {
        board.addLabels(cardIds, labels);
    }
}
