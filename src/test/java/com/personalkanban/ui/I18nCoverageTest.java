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

    private static final String BUNDLE_DIR = "src/main/resources/i18n";

    /**
     * Every bundle shipped with the app, discovered from the resources instead
     * of being listed by hand: adding a language later is automatically
     * covered by this test without touching it.
     */
    static List<String> bundles() throws IOException {
        Path dir = resolve(BUNDLE_DIR);
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("messages") && name.endsWith(".properties"))
                    .sorted()
                    .collect(Collectors.toList());
            assertThat(names)
                    .as("i18n bundles in %s (at least the default language)", dir)
                    .isNotEmpty();
            return names;
        }
    }

    /** The bundle of {@link I18n#DEFAULT_LOCALE}: the reference for the others. */
    static String referenceBundle() throws IOException {
        return defaultBundle();
    }

    static String defaultBundle() throws IOException {
        String expected = "messages_" + I18n.DEFAULT_LOCALE.getLanguage() + ".properties";
        assertThat(bundles())
                .as("a bundle for the default language %s", I18n.DEFAULT_LOCALE)
                .contains(expected);
        return expected;
    }

    private static Path resolve(String relative) {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd;
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve(relative);
            if (Files.isDirectory(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate " + relative + " from " + cwd);
    }

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

        for (String bundle : bundles()) {
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
        Properties defaults = loadBundle(defaultBundle());
        assertThat(defaults.getProperty("dialog.ok")).isNotBlank();
        assertThat(defaults.getProperty("dialog.cancel")).isNotBlank();
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

        for (String bundle : bundles()) {
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
