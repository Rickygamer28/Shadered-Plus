package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;

import java.util.HashMap;
import java.util.Map;

/** A few log lines per kind of event, to see in latest.log what the renderers do without flooding it. */
final class Debug {
    private static final Map<String, Integer> COUNTS = new HashMap<>();

    private Debug() {}

    static void once(String key, String message, Object... args) {
        log(key, 1, message, args);
    }

    static void few(String key, String message, Object... args) {
        log(key, 3, message, args);
    }

    static boolean firstTime(String key) {
        return COUNTS.merge(key, 1, Integer::sum) == 1;
    }

    private static void log(String key, int max, String message, Object... args) {
        int n = COUNTS.merge(key, 1, Integer::sum);
        if (n <= max) {
            ZinogreMod.LOGGER.info(message, args);
        }
    }
}
