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

    /** Every bundle shipped with the app (base = English). */
    private static final List<String> BUNDLES = List.of(
            "messages.properties",
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

    private static Set<String> collectUsedKeys(Path uiRoot) throws IOException {
        Set<String> keys = new HashSet<>();
        try (Stream<Path> files = Files.walk(uiRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Matcher matcher = TEXT_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
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
