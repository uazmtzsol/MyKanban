package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ProcessId;

import java.util.Objects;

/** Renames a process (session 4.6), one undoable step. */
public final class RenameProcessCommand implements BoardCommand {

    private final ProcessId processId;
    private final String newName;

    public RenameProcessCommand(ProcessId processId, String newName) {
        this.processId = Objects.requireNonNull(processId);
        this.newName = Objects.requireNonNull(newName);
    }

    @Override
    public void execute(Board board) {
        board.renameProcess(processId, newName);
    }
}
