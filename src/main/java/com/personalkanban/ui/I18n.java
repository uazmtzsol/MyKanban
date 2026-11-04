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
 *
 * <p>Only Spanish ships today, but everything stays per-locale on purpose:
 * dropping {@code i18n/messages_<lang>.properties} into the resources and
 * adding that locale to {@link #SUPPORTED} is all a new language needs — no
 * other code changes.</p>
 */
final class I18n {

    static final String BUNDLE_NAME = "i18n.messages";

    /** The language the app falls back to when the requested one has no bundle. */
    static final Locale DEFAULT_LOCALE = Locale.of("es");

    /** The languages offered in the menu: Spanish is the only one for now. */
    static final List<Locale> SUPPORTED = List.of(DEFAULT_LOCALE);

    private ResourceBundle bundle;

    I18n(Locale locale) {
        setLocale(locale);
    }

    void setLocale(Locale locale) {
        Objects.requireNonNull(locale);
        // A locale without a bundle (a settings file from an older build that
        // still remembers English, or the JVM's own locale) shows the default
        // language instead of leaving the UI blank or failing to start.
        ResourceBundle loaded = load(locale);
        if (loaded == null) {
            loaded = load(DEFAULT_LOCALE);
        }
        if (loaded == null) {
            throw new IllegalStateException("Missing i18n bundle: " + BUNDLE_NAME);
        }
        this.bundle = loaded;
    }

    private static ResourceBundle load(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale, new UTF8Control());
        } catch (MissingResourceException e) {
            return null;
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

    /** The locale actually on screen (the fallback when the request had no bundle). */
    Locale locale() {
        Locale actual = bundle.getLocale();
        return actual.equals(Locale.ROOT) ? DEFAULT_LOCALE : actual;
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
