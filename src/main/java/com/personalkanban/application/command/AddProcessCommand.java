package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.Process;

import java.util.Objects;

/** Creates a process (session 4.6), one undoable step. */
public final class AddProcessCommand implements BoardCommand {

    private final String name;

    private Process created;

    public AddProcessCommand(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public void execute(Board board) {
        created = board.addProcess(name);
    }

    public Process createdProcess() {
        return Objects.requireNonNull(created, "command not executed yet");
    }
}
