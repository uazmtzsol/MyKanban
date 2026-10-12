package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the "buttons show the property name" bug: every i18n key
 * referenced from UI code must exist in ALL language bundles, otherwise
 * {@link I18n#text} silently renders the raw key on screen. Scans the ui
 * sources for {@code i18n.text("...")} literals and checks each key against
 * every {@code messages*.properties} file.
 */
class I18nCoverageTest {

    private static final Pattern TEXT_KEY =
            Pattern.compile("i18n\\.text\\(\"([^\"]+)\"");

    /** Any literal shaped like a key: "card.process.label". */
    private static final Pattern DOTTED_LITERAL =
            Pattern.compile("\"([a-z][A-Za-z0-9]*(?:\\.[A-Za-z0-9]+)+)\"");

    /**
     * Dotted literals that are not translations (file names, resource names).
     * Everything else with that shape must be a key of every bundle.
     */
    private static final Set<String> NOT_TRANSLATIONS = Set.of(
            "board.json", "board.pdf", "kanban.db", "i18n.messages", "user.home");

    /** Every bundle shipped with the app (base = English, plus the
     *  explicit English copy that prevents JVM fallback to the system locale). */
    private static final List<String> BUNDLES = List.of(
            "messages.properties",
            "messages_en.properties",
            "messages_de.properties",
            "messages_es.properties",
            "messages_fr.properties");

    private static Path uiSourcesRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd;
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui");
            if (Files.isDirectory(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate src/main/java/com/personalkanban/ui from " + cwd);
    }

    @Test
    void everyI18nKeyUsedInUiExistsInEveryBundle() throws IOException {
        Set<String> usedKeys = collectUsedKeys(uiSourcesRoot());
        assertThat(usedKeys)
                .as("expected i18n.text(\"...\") usages to be found in ui sources")
                .isNotEmpty();

        for (String bundle : BUNDLES) {
            Properties properties = loadBundle(bundle);
            Set<String> missing = usedKeys.stream()
                    .filter(key -> !properties.containsKey(key))
                    .collect(Collectors.toSet());
            assertThat(missing)
                    .as("keys used in ui code but missing from %s", bundle)
                    .isEmpty();
        }
    }

    @Test
    void wellKnownDialogButtonsAreTranslated() throws IOException {
        // Regression anchor for the markdown editor buttons bug.
        Properties english = loadBundle("messages.properties");
        Properties spanish = loadBundle("messages_es.properties");
        assertThat(english.getProperty("dialog.ok")).isNotBlank();
        assertThat(english.getProperty("dialog.cancel")).isNotBlank();
        assertThat(spanish.getProperty("dialog.ok")).isNotBlank();
        assertThat(spanish.getProperty("dialog.cancel")).isNotBlank();
    }

    /**
     * Second net for the same class of bug (session 13: the bulk-process
     * button rendered the raw "bulk.process" text). A key handed to a helper
     * as an argument — {@code toolButton("⛭", "bulk.process")},
     * {@code itemOf("file.save", …)} — never appears inside an
     * {@code i18n.text("…")} literal, so the scan above cannot see it, and
     * {@link I18n#text} then quietly shows the key itself on screen.
     */
    @Test
    void everyDottedStringLiteralInUiIsAKnownKey() throws IOException {
        Set<String> literals = collectDottedLiterals(uiSourcesRoot());
        assertThat(literals)
                .as("expected dotted literals to be found in ui sources")
                .isNotEmpty();

        for (String bundle : BUNDLES) {
            Properties properties = loadBundle(bundle);
            Set<String> unknown = literals.stream()
                    .filter(literal -> !properties.containsKey(literal))
                    .filter(literal -> !NOT_TRANSLATIONS.contains(literal))
                    .collect(Collectors.toSet());
            assertThat(unknown)
                    .as("dotted literals in ui code that are not keys of %s", bundle)
                    .isEmpty();
        }
    }

    private static Set<String> collectDottedLiterals(Path uiRoot) throws IOException {
        Set<String> literals = new HashSet<>();
        try (Stream<Path> files = Files.walk(uiRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Matcher matcher = DOTTED_LITERAL.matcher(
                        Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    literals.add(matcher.group(1));
                }
            }
        }
        return literals;
    }

    private static Set<String> collectUsedKeys(Path uiRoot) throws IOException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> files = Files.walk(uiRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Matcher matcher = TEXT_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // A trailing dot means the literal is a dynamic prefix
                    // (e.g. i18n.text("prefs.scheme." + slot.key())): the
                    // concrete keys are validated by the bundles themselves.
                    if (!key.endsWith(".")) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private static Properties loadBundle(String fileName) throws IOException {
        Properties properties = new Properties();
        String resource = "/i18n/" + fileName;
        try (InputStream in = I18nCoverageTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("bundle %s on classpath", resource).isNotNull();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
