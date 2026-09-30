package com.personalkanban.domain.board;

/**
 * Public immutable DTO describing one process (session 4.6): part of the
 * board snapshot shared by persistence and undo/redo.
 */
public record ProcessSnapshot(ProcessId id, String name) {

    public ProcessSnapshot {
        if (id == null || name == null) {
            throw new IllegalArgumentException("Process snapshot fields must not be null");
        }
    }

    static ProcessSnapshot from(Process process) {
        return new ProcessSnapshot(process.id(), process.name());
    }

    Process toProcess() {
        return new Process(id(), name());
    }
}
