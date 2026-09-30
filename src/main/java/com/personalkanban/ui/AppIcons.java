package com.personalkanban.ui;

import javafx.scene.image.Image;

import java.io.InputStream;
import java.util.List;

/**
 * Loads the application icon set (session 4.6 request: the taskbar showed
 * the default Java coffee cup). PNGs live under /icons and were generated
 * from a rounded-corner monogram; every size is registered so Windows can
 * pick the sharpest one for taskbar, title bar and Alt-Tab.
 */
public final class AppIcons {

    private static final List<String> PATHS = List.of(
            "/icons/app-16.png", "/icons/app-32.png", "/icons/app-48.png");

    private AppIcons() {
    }

    /** Every icon variant; empty list only if the resources went missing. */
    public static List<Image> all() {
        return PATHS.stream()
                .map(AppIcons::load)
                .filter(image -> image != null)
                .toList();
    }

    private static Image load(String path) {
        try (InputStream in = AppIcons.class.getResourceAsStream(path)) {
            return in == null ? null : new Image(in);
        } catch (Exception e) {
            // Icons are cosmetic: a missing resource must never block startup.
            return null;
        }
    }
}
