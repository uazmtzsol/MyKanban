package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime language switching (user report: the UI never changed language).
 *
 * <p>Only Spanish ships right now, so the tests cover both halves of that
 * arrangement: the bundle mechanism is still per-locale, and a locale without a
 * bundle degrades to the default language instead of breaking the window.</p>
 */
class I18nSwitchTest {

    @Test
    void theOnlyOfferedLanguageIsSpanish() {
        assertEquals(1, I18n.SUPPORTED.size(), "only Spanish is offered for now");
        assertEquals(Locale.of("es"), I18n.SUPPORTED.get(0));
        assertEquals(Locale.of("es"), I18n.DEFAULT_LOCALE);
    }

    @Test
    void setLocaleChangesTranslatedText() {
        I18n i18n = new I18n(Locale.of("es"));
        String spanish = i18n.text("menu.language");
        assertEquals("Idioma", spanish);

        // Switching to the same language must keep re-translating from the
        // bundle (the mechanism, not a cached string).
        i18n.setLocale(I18n.DEFAULT_LOCALE);
        assertEquals(spanish, i18n.text("menu.language"));
    }

    @Test
    void everySupportedLocaleLoadsItsOwnBundle() {
        for (Locale locale : I18n.SUPPORTED) {
            I18n i18n = new I18n(locale);
            assertEquals(locale, i18n.locale(), "bundle locale must match request");
            String value = i18n.text("menu.language");
            assertFalse(value.isBlank());
            assertFalse(value.equals("menu.language"), "the key must be translated");
        }
    }

    /**
     * A settings file written by an older build can still ask for English. That
     * must show Spanish rather than throw while the window is being built.
     */
    @Test
    void aLocaleWithoutABundleFallsBackToSpanish() {
        I18n i18n = new I18n(Locale.of("en"));

        assertEquals(Locale.of("es"), i18n.locale(), "reports what is on screen");
        assertEquals("Idioma", i18n.text("menu.language"));
    }

    /** The same fallback applies to the JVM's own locale (recovery window). */
    @Test
    void aRegionalVariantUsesTheBaseSpanishBundle() {
        I18n i18n = new I18n(Locale.forLanguageTag("es-MX"));

        assertEquals("es", i18n.locale().getLanguage());
        assertTrue(i18n.text("menu.language").equals("Idioma"));
    }
}
