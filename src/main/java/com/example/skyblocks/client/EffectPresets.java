package com.example.skyblocks.client;

import com.example.skyblocks.EffectBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * The Effect Block's presets: eight looks kept on this computer (config/shadered_plus_effect_presets.json), to put on
 * any effect block in one click. They start with a few ready-made ones.
 */
public final class EffectPresets {
    public static final int SLOTS = 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static EffectBlockEntity.Settings[] slots;

    private EffectPresets() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("shadered_plus_effect_presets.json");
    }

    @Nullable
    public static EffectBlockEntity.Settings get(int i) {
        load();
        return slots[i];
    }

    public static void set(int i, EffectBlockEntity.Settings s) {
        load();
        slots[i] = s;
        save();
    }

    private static void load() {
        if (slots != null) {
            return;
        }
        slots = defaults();
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonArray a = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonArray();
                for (int i = 0; i < SLOTS && i < a.size(); i++) {
                    slots[i] = a.get(i).isJsonObject() ? fromJson(a.get(i).getAsJsonObject()) : null;
                }
            }
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't read the effect block presets", e);
        }
    }

    private static void save() {
        JsonArray a = new JsonArray();
        for (EffectBlockEntity.Settings s : slots) {
            a.add(s == null ? JsonNull.INSTANCE : toJson(s));
        }
        try {
            Files.writeString(file(), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SkyBlocksMod.LOGGER.warn("Shadered+: couldn't save the effect block presets", e);
        }
    }

    private static JsonObject toJson(EffectBlockEntity.Settings s) {
        JsonObject o = new JsonObject();
        o.addProperty("effect", EffectBlockEntity.EFFECTS[s.effect()]);
        o.addProperty("count", s.count());
        JsonArray c = new JsonArray();
        for (int v : s.colours()) {
            c.add(String.format("#%06X", v));
        }
        o.add("colours", c);
        o.addProperty("speed", s.speed());
        o.addProperty("size", s.size());
        o.addProperty("brightness", s.brightness());
        o.addProperty("pixel", s.pixel());
        o.addProperty("blur", s.blur());
        if (!s.block().isEmpty()) {
            o.addProperty("block", s.block());
        }
        return o;
    }

    private static EffectBlockEntity.Settings fromJson(JsonObject o) {
        int effect = java.util.Arrays.asList(EffectBlockEntity.EFFECTS).indexOf(o.get("effect").getAsString());
        int[] c = new int[EffectBlockEntity.MAX_COLOURS + 1];
        JsonArray a = o.getAsJsonArray("colours");
        for (int i = 0; i < c.length && i < a.size(); i++) {
            JsonElement e = a.get(i);
            c[i] = Integer.parseInt(e.getAsString().replace("#", ""), 16);
        }
        return new EffectBlockEntity.Settings(Math.max(effect, 0), o.get("count").getAsInt(), c, o.get("speed").getAsInt(),
                o.get("size").getAsInt(), o.get("brightness").getAsInt(), o.get("pixel").getAsBoolean(), o.get("blur").getAsInt(),
                o.has("block") ? o.get("block").getAsString() : "");
    }

    private static EffectBlockEntity.Settings preset(String effect, int speed, int size, int... colours) {
        int[] c = new int[EffectBlockEntity.MAX_COLOURS + 1];
        for (int i = 0; i < colours.length - 1 && i < EffectBlockEntity.MAX_COLOURS; i++) {
            c[i] = colours[i];
        }
        c[EffectBlockEntity.BACKGROUND] = colours[colours.length - 1]; // (the last: the background)
        return new EffectBlockEntity.Settings(java.util.Arrays.asList(EffectBlockEntity.EFFECTS).indexOf(effect), colours.length - 1, c,
                speed, size, 100, false, 0);
    }

    /** The ready-made ones (colours, then the background last). */
    private static EffectBlockEntity.Settings[] defaults() {
        EffectBlockEntity.Settings[] d = new EffectBlockEntity.Settings[SLOTS];
        d[0] = preset("swirl", 100, 100, 0xDE443B, 0x006BB4, 0x162325, 0x000000);
        d[1] = preset("fire", 100, 100, 0x3A0A00, 0xC81E00, 0xFF7A00, 0xFFD23C, 0x000000);
        d[2] = preset("rain", 100, 100, 0x30FF6A, 0x00B050, 0x000800);
        d[3] = preset("wave", 60, 150, 0xFF71CE, 0x01CDFE, 0x05FFA1, 0xB967FF, 0xFFFB96, 0x000000);
        d[4] = preset("ink", 80, 100, 0x00E5FF, 0x0A2A6B, 0x7A00FF, 0x0B0B1E, 0x000000);
        d[5] = preset("spectrum", 100, 100, 0x00E5FF, 0x7A00FF, 0xFF2D95, 0x05030F);
        return d;
    }
}
