package com.example.skyblocks.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Which kinds of fish the aquariums show (sneak + right-click an aquarium to choose), kept in
 * config/skyblocks-aquarium.properties on this computer. 0-9 the normal aquarium's fish, 10-19 the ocean aquarium's, 20-25 the creatures on the bottom,
 * 26-32 the game's own water mobs (cod, salmon, tropical fish, tadpole, squid, glow squid, axolotl).
 */
public final class AquariumSettings {
    public static final String[] NAMES = {"clownfish", "blue_tang", "yellow_tang", "neon_tetra", "angelfish", "goldfish", "betta",
            "pufferfish", "guppy", "discus", "shark", "tuna", "manta_ray", "sea_turtle", "grouper", "jellyfish", "dolphin", "sardine",
            "ocean_sunfish", "basking_shark", "hermit_crab", "crab", "stingray", "octopus", "lobster", "shrimp",
            "cod", "salmon", "tropical_fish", "tadpole", "squid", "glow_squid", "axolotl", "anglerfish", "lanternfish"};
    private static final boolean[] ENABLED = new boolean[NAMES.length];
    private static boolean loaded;
    private static boolean nightMode = true;
    /** The tanks' light (normal, ocean): 0 daylight, 1 moonlight, 2 sunset, 3 reef. */
    public static final String[] LIGHTS = {"daylight", "moonlight", "sunset", "reef"};
    private static final int[] LIGHT = new int[2];

    private AquariumSettings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("skyblocks-aquarium.properties");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        java.util.Arrays.fill(ENABLED, true);
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
            for (int i = 0; i < NAMES.length; i++) {
                ENABLED[i] = !"false".equalsIgnoreCase(p.getProperty(NAMES[i], "true").trim());
            }
            nightMode = !"false".equalsIgnoreCase(p.getProperty("night_mode", "true").trim());
            for (int o = 0; o < 2; o++) {
                LIGHT[o] = Math.max(0, java.util.Arrays.asList(LIGHTS).indexOf(p.getProperty(o == 0 ? "light" : "ocean_light", "daylight").trim()));
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // (a broken file: everything on)
        }
    }

    public static boolean enabled(int kind) {
        load();
        return kind >= 0 && kind < ENABLED.length && ENABLED[kind];
    }

    /** Whether the tanks go dark at night (their lights dimmed, jellyfish glowing, fish resting). */
    public static boolean nightMode() {
        load();
        return nightMode;
    }

    /** The light this type of tank has (0 daylight, 1 moonlight, 2 sunset, 3 reef). */
    public static int light(boolean ocean) {
        load();
        return LIGHT[ocean ? 1 : 0];
    }

    public static void setLight(boolean ocean, int light) {
        load();
        LIGHT[ocean ? 1 : 0] = Math.floorMod(light, LIGHTS.length);
        save();
    }

    public static void setNightMode(boolean on) {
        load();
        nightMode = on;
        save();
    }

    public static void set(int kind, boolean on) {
        load();
        ENABLED[kind] = on;
        save();
        AquariumRenderer.settingsChanged();
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("night_mode", Boolean.toString(nightMode));
        p.setProperty("light", LIGHTS[LIGHT[0]]);
        p.setProperty("ocean_light", LIGHTS[LIGHT[1]]);
        for (int i = 0; i < NAMES.length; i++) {
            p.setProperty(NAMES[i], Boolean.toString(ENABLED[i]));
        }
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Shadered+ aquariums: which fish are shown");
        } catch (IOException ignored) {
            // (can't save: it still applies until the game closes)
        }
    }
}
