package com.personalkanban;

/**
 * Plain launcher for the shaded portable jar. The JVM's built-in launcher
 * refuses to start classes that extend {@code javafx.application.Application}
 * unless JavaFX is on the module path, so this thin, non-Application entry
 * point takes over for {@code java -jar personal-kanban.jar}; it delegates to
 * {@link Main}, which is the real JavaFX application. Maven's javafx:run keeps
 * using {@code Main} directly during development.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
