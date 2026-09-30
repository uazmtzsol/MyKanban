package com.personalkanban.ui;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Thin wrapper over {@link ResourceBundle} (Pure Fabrication): translates keys
 * to text for the current locale and exposes the supported locales for the
 * language menu. Bundle name: {@code i18n.messages}.
 */
final class I18n {

    static final String BUNDLE_NAME = "i18n.messages";

    /** The maintained languages: English, French, Spanish, German. */
    static final List<Locale> SUPPORTED = List.of(
            Locale.of("en"), Locale.of("fr"), Locale.of("es"), Locale.of("de"));

    private ResourceBundle bundle;

    I18n(Locale locale) {
        setLocale(locale);
    }

    void setLocale(Locale locale) {
        Objects.requireNonNull(locale);
        try {
            this.bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale,
                    new UTF8Control());
        } catch (MissingResourceException e) {
            throw new IllegalStateException("Missing i18n bundle: " + BUNDLE_NAME, e);
        }
    }

    /** Translates a key, falling back to the key itself when missing. */
    String text(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    String text(String key, Object... args) {
        return java.text.MessageFormat.format(text(key), args);
    }

    Locale locale() {
        return bundle.getLocale().equals(new Locale("")) ? Locale.of("en") : bundle.getLocale();
    }

    static final class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, java.io.IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            try (var in = loader.getResourceAsStream(resourceName)) {
                if (in == null) {
                    return null;
                }
                return new PropertyResourceBundleUtf8(in);
            }
        }
    }

    private static final class PropertyResourceBundleUtf8 extends ResourceBundle {
        private final java.util.Map<String, Object> lookup;

        PropertyResourceBundleUtf8(java.io.InputStream stream) throws java.io.IOException {
            var properties = new java.util.Properties();
            properties.load(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            var map = new java.util.HashMap<String, Object>();
            properties.forEach((key, value) -> map.put(String.valueOf(key), value));
            this.lookup = map;
        }

        @Override
        protected Object handleGetObject(String key) {
            return lookup.get(key);
        }

        @Override
        public java.util.Enumeration<String> getKeys() {
            return java.util.Collections.enumeration(lookup.keySet());
        }
    }
}
