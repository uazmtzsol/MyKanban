package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ProcessId;

import java.util.Objects;

/** Deletes a process (session 4.6): member cards become unassigned. */
public final class RemoveProcessCommand implements BoardCommand {

    private final ProcessId processId;

    public RemoveProcessCommand(ProcessId processId) {
        this.processId = Objects.requireNonNull(processId);
    }

    @Override
    public void execute(Board board) {
        board.removeProcess(processId);
    }
}
