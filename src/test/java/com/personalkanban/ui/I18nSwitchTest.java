package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Runtime language switching (user report: the UI never changed language). */
class I18nSwitchTest {

    @Test
    void setLocaleChangesTranslatedText() {
        I18n i18n = new I18n(Locale.of("es"));
        String spanish = i18n.text("menu.language");

        i18n.setLocale(Locale.of("en"));
        String english = i18n.text("menu.language");

        assertNotEquals(spanish, english, "setLocale must re-translate");
        assertEquals("Language", english, "English bundle must be loaded");
    }

    @Test
    void switchingBackToSpanishRestoresSpanishText() {
        I18n i18n = new I18n(Locale.of("en"));
        i18n.setLocale(Locale.of("es"));
        assertEquals("Idioma", i18n.text("menu.language"));
    }

    @Test
    void everySupportedLocaleLoadsItsOwnBundle() {
        for (Locale locale : I18n.SUPPORTED) {
            I18n i18n = new I18n(locale);
            String value = i18n.text("menu.language");
            assertEquals(locale, i18n.locale(), "bundle locale must match request");
            org.junit.jupiter.api.Assertions.assertFalse(value.isBlank());
        }
    }
}
