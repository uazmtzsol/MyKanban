package com.personalkanban.domain.board;

/**
 * A named group of related cards working toward one goal (session 4.6,
 * user request "Procesos"): a process lets the user mark which cards belong
 * together, define precedence order between them, and filter the board down
 * to that process. It is not a container — cards reference it by id and may
 * live in any column.
 */
public final class Process {

    private final ProcessId id;
    private String name;

    /** Creates a fresh process with a new identity. */
    public Process(String name) {
        this(new ProcessId(java.util.UUID.randomUUID().toString()), name);
    }

    /** Full constructor used by persistence/undo restore (stable id). */
    public Process(ProcessId id, String name) {
        if (id == null) {
            throw new IllegalArgumentException("Process id must not be null");
        }
        this.id = id;
        rename(name);
    }

    public ProcessId id() {
        return id;
    }

    public String name() {
        return name;
    }

    void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Process name must not be blank");
        }
        this.name = newName.strip();
    }

    @Override
    public String toString() {
        return name; // friendly default rendering in JavaFX controls
    }
}
