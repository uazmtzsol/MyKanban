package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of {@link I18nCoverageTest}: that one proves a key exists in
 * every bundle, this one proves the <b>value</b> is a translation and not a
 * broken line of the file.
 *
 * <p>Motivated by a real mistake (T17 · H-3): the recovery dialog's text was
 * written as three consecutive lines, so the paragraphs after the first became
 * keys of their own and the sentence the user needed — the path of the copy —
 * never reached the screen. A missing key renders as the key itself; a value
 * split by accident renders as a truncated sentence, which is much harder to
 * notice. Both are caught here.</p>
 */
class I18nBundleHygieneTest {

    private static final List<String> BUNDLES = List.of(
            "messages.properties",
            "messages_en.properties",
            "messages_de.properties",
            "messages_es.properties",
            "messages_fr.properties");

    /** A real key: dotted words, no spaces, no braces. */
    private static final Pattern KEY = Pattern.compile("[a-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    /** Argument placeholders of a translation: {0}, {1}… */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    @Test
    void everyLineIsEitherAKeyOrAComment() throws IOException {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            Set<String> odd = new TreeSet<>();
            for (String key : properties.stringPropertyNames()) {
                if (!KEY.matcher(key).matches()) {
                    odd.add(key);
                }
            }
            assertThat(odd)
                    .as("lines in %s that became keys by accident (a wrapped value,"
                            + " a stray paragraph or a placeholder line)", bundle)
                    .isEmpty();
        }
    }

    @Test
    void aTranslationKeepsTheSameArgumentsAsTheOthers() throws IOException {
        Map<String, Map<String, String>> texts = new LinkedHashMap<>();
        for (String bundle : BUNDLES) {
            texts.put(bundle, texts(load(bundle)));
        }

        for (Map.Entry<String, Map<String, String>> entry : texts.entrySet()) {
            String bundle = entry.getKey();
            Set<String> keys = new TreeSet<>(texts.get(BUNDLES.get(0)).keySet());
            keys.addAll(entry.getValue().keySet());
            for (String key : keys) {
                Set<String> reference = placeholders(texts.get(BUNDLES.get(0)).get(key));
                Set<String> translated = placeholders(entry.getValue().get(key));
                assertThat(translated)
                        .as("arguments of %s in %s (reference: %s)", key, bundle, reference)
                        .isEqualTo(reference);
            }
        }
    }

    /** Every translation with arguments must format without blowing up. */
    @Test
    void everyTranslationFormatsWithItsOwnArguments() throws IOException {
        for (String bundle : BUNDLES) {
            for (Map.Entry<String, String> text : texts(load(bundle)).entrySet()) {
                Set<String> placeholders = placeholders(text.getValue());
                Object[] args = new Object[highest(placeholders) + 1];
                for (int i = 0; i < args.length; i++) {
                    args[i] = "X" + i;
                }
                String formatted = MessageFormat.format(text.getValue(), args);
                assertThat(formatted)
                        .as("%s in %s still shows an unformatted placeholder",
                                text.getKey(), bundle)
                        .doesNotContain("{0}")
                        .doesNotContain("{1}")
                        .doesNotContain("{2}");
            }
        }
    }

    /** The recovery text keeps its whole message (T17 · H-3 regression). */
    @Test
    void theRecoveryMessageKeepsEveryParagraph() throws IOException {
        for (String bundle : BUNDLES) {
            String body = texts(load(bundle)).get("recovery.corrupt.body");
            assertThat(body).as("recovery.corrupt.body in %s", bundle).isNotBlank();
            String formatted = MessageFormat.format(body, "/ruta/kanban.db", "/ruta/backups/kanban-x.db");
            assertThat(formatted)
                    .as("the damaged file and the copy both appear in %s", bundle)
                    .contains("/ruta/kanban.db")
                    .contains("/ruta/backups/kanban-x.db");
            assertThat(formatted.split("\\R").length)
                    .as("the copy has a paragraph of its own in %s", bundle)
                    .isGreaterThanOrEqualTo(3);
        }
    }

    private static Map<String, String> texts(Properties properties) {
        Map<String, String> texts = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            texts.put(key, properties.getProperty(key));
        }
        return texts;
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        if (text == null) {
            return found;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static int highest(Set<String> placeholders) {
        return placeholders.stream().mapToInt(Integer::parseInt).max().orElse(-1);
    }

    private static Properties load(String fileName) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = I18nCoverageTest.class.getResourceAsStream("/i18n/" + fileName)) {
            assertThat(in).as("bundle %s on classpath", fileName).isNotNull();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
