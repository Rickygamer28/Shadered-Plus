package com.example.skyblocks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Hands a studio sky's settings (StudioSkies) to its shader: P0 .., four at a time (StudioSpec), and the world's time of
 * day and weather (DayTime, Weather: for the day and night, and weather layers).
 */
public final class StudioSkiesClient {
    public static void apply(ShaderInstance shader, float[] p) {
        Minecraft mc = Minecraft.getInstance();
        float day = 0.0F, rain = 0.0F, thunder = 0.0F;
        if (mc.level != null) {
            float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
            day = mc.level.getTimeOfDay(pt);
            rain = mc.level.getRainLevel(pt);
            thunder = mc.level.getThunderLevel(pt);
        }
        apply(shader, p, day, rain, thunder);
    }

    /** As the world is, or (the studio's preview) at another time of day and weather. */
    public static void apply(ShaderInstance shader, float[] p, float day, float rain, float thunder) {
        for (int i = 0; i < com.example.skyblocks.StudioSpec.VECS; i++) {
            shader.safeGetUniform("P" + i).set(at(p, i * 4), at(p, i * 4 + 1), at(p, i * 4 + 2), at(p, i * 4 + 3));
        }
        shader.safeGetUniform("DayTime").set(day);
        shader.safeGetUniform("Weather").set(rain, thunder);
    }

    private static float at(float[] p, int i) {
        return i < p.length ? p[i] : 0.0F;
    }

    private StudioSkiesClient() {}
}
