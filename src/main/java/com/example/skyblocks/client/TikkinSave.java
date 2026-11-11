package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.arcade.Showdown;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Tikkin, remembered on this computer (config/skyblocks-tikkin.txt): NYX unlocked; and for each fighter, how you fight
 * with it (what Showdown learned - situation:action counts) - your ghost, which goes to each cabinet you play at.
 */
public final class TikkinSave {
    private TikkinSave() {}

    private static boolean loaded;
    private static final Map<Integer, Map<String, Integer>> PROFILES = new HashMap<>();
    /** Most remembered of each fighter (older ways fade as it learns more). */
    private static final int MAX_KEYS = 400, MAX_TOTAL = 6000;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("skyblocks-tikkin.txt");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Showdown.saveUnlocks = TikkinSave::save;
        try {
            if (!Files.exists(file())) return;
            for (String line : Files.readAllLines(file())) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                if (k.equals("nyx")) {
                    Showdown.nyxUnlocked = v.trim().equals("1");
                } else if (k.startsWith("ghost.")) {
                    int f = Integer.parseInt(k.substring(6));
                    Map<String, Integer> m = PROFILES.computeIfAbsent(f, x -> new HashMap<>());
                    for (String part : v.split(";")) {
                        int e = part.lastIndexOf('=');
                        if (e > 0) m.merge(part.substring(0, e), Integer.parseInt(part.substring(e + 1)), Integer::sum);
                    }
                }
            }
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.warn("Couldn't read Tikkin's save: {}", e.toString());
        }
    }

    public static void save() {
        List<String> lines = new ArrayList<>();
        lines.add("nyx=" + (Showdown.nyxUnlocked ? 1 : 0));
        for (var e : PROFILES.entrySet()) lines.add("ghost." + e.getKey() + "=" + profile(e.getKey()));
        try {
            Files.write(file(), lines);
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.warn("Couldn't save Tikkin: {}", e.toString());
        }
    }

    /** What was just learned (fighter|key -> times) added in; which fighters, and how much each got. */
    public static Map<Integer, Integer> merge(Map<String, Integer> learned) {
        Map<Integer, Integer> got = new HashMap<>();
        Set<Integer> touched = new HashSet<>();
        for (var e : learned.entrySet()) {
            int bar = e.getKey().indexOf('|');
            if (bar <= 0) continue;
            try {
                int f = Integer.parseInt(e.getKey().substring(0, bar));
                PROFILES.computeIfAbsent(f, x -> new HashMap<>()).merge(e.getKey().substring(bar + 1), e.getValue(), Integer::sum);
                got.merge(f, e.getValue(), Integer::sum);
                touched.add(f);
            } catch (NumberFormatException ignored) {
                // (not one of ours)
            }
        }
        for (int f : touched) trim(PROFILES.get(f));
        return got;
    }

    /** Older ways fading: past so much, everything halved; only the most-done kept. */
    private static void trim(Map<String, Integer> m) {
        int total = 0;
        for (int v : m.values()) total += v;
        if (total > MAX_TOTAL) {
            m.replaceAll((k, v) -> v / 2);
            m.values().removeIf(v -> v <= 0);
        }
        if (m.size() > MAX_KEYS) {
            List<Map.Entry<String, Integer>> all = new ArrayList<>(m.entrySet());
            all.sort((a, b) -> b.getValue() - a.getValue());
            Map<String, Integer> keep = new HashMap<>();
            for (int i = 0; i < MAX_KEYS; i++) keep.put(all.get(i).getKey(), all.get(i).getValue());
            m.clear();
            m.putAll(keep);
        }
    }

    /** A fighter's profile, as the cabinet keeps it: key=times;... (the most-done first). */
    public static String profile(int fighter) {
        Map<String, Integer> m = PROFILES.getOrDefault(fighter, Map.of());
        List<Map.Entry<String, Integer>> all = new ArrayList<>(m.entrySet());
        all.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (var e : all) {
            String part = e.getKey() + "=" + e.getValue() + ";";
            if (sb.length() + part.length() > 15000) break;
            sb.append(part);
        }
        return sb.toString();
    }
}
