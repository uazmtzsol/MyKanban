package com.personalkanban;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Build stamp shown in the window title so any running instance can be
 * identified at a glance ("v2d-20260929-1854"). Regenerated from the build
 * timestamp at class-load; when the timestamp resource is missing (raw IDE
 * run before a build), it degrades to a fixed dev marker.
 */
public final class AppVersion {

    private static final String DEV = "dev";

    private static String stamp;

    private AppVersion() {
    }

    public static synchronized String stamp() {
        if (stamp == null) {
            stamp = readStamp();
        }
        return stamp;
    }

    private static String readStamp() {
        try (var in = AppVersion.class.getResourceAsStream("/version.properties")) {
            if (in == null) {
                return DEV;
            }
            var props = new java.util.Properties();
            props.load(in);
            String value = props.getProperty("build.stamp", DEV).strip();
            return value.isEmpty() ? DEV : value;
        } catch (Exception e) {
            return DEV;
        }
    }

    /** Writes the stamp resource at build time (called from the Maven profile). */
    public static void main(String[] args) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        var props = new java.util.Properties();
        props.setProperty("build.stamp", "v2d-" + now);
        var out = System.out; // placeholder: replaced by exec below
        if (args.length == 1) {
            try (var fos = new java.io.FileOutputStream(args[0])) {
                props.store(fos, "Generated at build time - do not edit");
                return;
            } catch (Exception e) {
                System.err.println("Could not write version stamp: " + e.getMessage());
            }
        }
        out.println(props.getProperty("build.stamp"));
    }
}
