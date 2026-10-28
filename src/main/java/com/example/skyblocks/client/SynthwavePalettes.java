package com.example.skyblocks.client;

import java.util.Map;
import net.minecraft.client.renderer.ShaderInstance;

/** The colours of each synthwave sky (the same scene, synthwave_sky.fsh, in different colours). */
public final class SynthwavePalettes {
    private static final String[] UNIFORMS = {"TOP", "MID", "MID_COOL", "HORIZON", "HORIZON_COOL", "PINK", "ORANGE", "YELLOW", "WIRE", "FLOOR", "ROCK", "SILHOUETTE", "ACCENT", "SUN0", "SUN1", "SUN2"};

    /** Per sky: 16 colours (r, g, b) in UNIFORMS order, then 1 for a moon instead of the sun. */
    private static final Map<String, float[]> PALETTES = Map.ofEntries(
            Map.entry("synthwave", new float[] {0.05F, 0.01F, 0.13F, 0.42F, 0.05F, 0.45F, 0.24F, 0.04F, 0.4F, 1F, 0.4F, 0.42F, 0.8F, 0.18F, 0.62F, 1F, 0.16F, 0.62F, 1F, 0.52F, 0.12F, 1F, 0.86F, 0.24F, 0.95F, 0.35F, 1F, 0.05F, 0F, 0.1F, 0.07F, 0.01F, 0.13F, 0.035F, 0F, 0.07F, 0.4F, 0.9F, 1F, 1F, 0.16F, 0.62F, 1F, 0.52F, 0.12F, 1F, 0.86F, 0.24F, 0F}),
            Map.entry("synthwave_outrun", new float[] {0F, 0F, 0.03F, 0.04F, 0.02F, 0.18F, 0.01F, 0.03F, 0.14F, 0.9F, 0.1F, 0.55F, 0.1F, 0.35F, 0.6F, 0F, 0.85F, 1F, 1F, 0.15F, 0.75F, 0.7F, 1F, 1F, 0.2F, 0.8F, 1F, 0F, 0F, 0.05F, 0.01F, 0F, 0.06F, 0F, 0F, 0.03F, 1F, 0.2F, 0.8F, 0.85F, 0F, 0.6F, 1F, 0.2F, 0.65F, 1F, 0.6F, 0.9F, 0F}),
            Map.entry("synthwave_night", new float[] {0F, 0.01F, 0.06F, 0.05F, 0.07F, 0.3F, 0.04F, 0.04F, 0.22F, 0.35F, 0.3F, 0.85F, 0.25F, 0.2F, 0.6F, 0.3F, 0.45F, 1F, 0.65F, 0.35F, 1F, 0.85F, 0.85F, 1F, 0.5F, 0.4F, 1F, 0F, 0F, 0.07F, 0.02F, 0.01F, 0.1F, 0.01F, 0F, 0.05F, 0.6F, 0.9F, 1F, 0.75F, 0.75F, 0.95F, 0.85F, 0.85F, 1F, 0.95F, 0.95F, 1F, 1F}),
            Map.entry("synthwave_miami", new float[] {0F, 0.35F, 0.45F, 0.1F, 0.6F, 0.65F, 0.1F, 0.45F, 0.6F, 1F, 0.55F, 0.7F, 0.95F, 0.5F, 0.75F, 1F, 0.3F, 0.7F, 0.2F, 1F, 0.9F, 1F, 1F, 1F, 1F, 0.6F, 0.9F, 0.02F, 0.08F, 0.12F, 0.05F, 0.15F, 0.22F, 0.02F, 0.06F, 0.1F, 1F, 1F, 1F, 1F, 0.3F, 0.6F, 1F, 0.5F, 0.6F, 1F, 0.8F, 0.6F, 0F}),
            Map.entry("darksynth", new float[] {0F, 0F, 0F, 0.12F, 0F, 0.01F, 0.06F, 0F, 0.01F, 0.6F, 0.02F, 0.04F, 0.35F, 0.01F, 0.03F, 1F, 0.05F, 0.08F, 1F, 0.25F, 0.05F, 1F, 0.55F, 0.4F, 1F, 0.1F, 0.12F, 0.02F, 0F, 0F, 0.04F, 0F, 0.005F, 0.01F, 0F, 0F, 1F, 0.3F, 0.2F, 0.5F, 0F, 0.02F, 0.9F, 0.02F, 0.05F, 1F, 0.2F, 0.15F, 0F}),
            Map.entry("synthwave_terminal", new float[] {0F, 0.01F, 0F, 0F, 0.06F, 0.02F, 0F, 0.04F, 0.01F, 0.05F, 0.35F, 0.1F, 0.03F, 0.25F, 0.07F, 0.15F, 1F, 0.3F, 0.5F, 1F, 0.4F, 0.8F, 1F, 0.75F, 0.2F, 1F, 0.35F, 0F, 0.02F, 0F, 0F, 0.03F, 0.01F, 0F, 0.01F, 0F, 0.6F, 1F, 0.6F, 0.05F, 0.5F, 0.15F, 0.2F, 0.9F, 0.35F, 0.7F, 1F, 0.6F, 0F}),
            Map.entry("vaporwave", new float[] {0.35F, 0.3F, 0.6F, 0.7F, 0.55F, 0.85F, 0.6F, 0.55F, 0.9F, 1F, 0.75F, 0.75F, 0.85F, 0.7F, 0.95F, 1F, 0.55F, 0.85F, 0.45F, 0.95F, 0.9F, 1F, 0.95F, 0.7F, 0.9F, 0.6F, 1F, 0.18F, 0.12F, 0.3F, 0.35F, 0.25F, 0.5F, 0.25F, 0.15F, 0.35F, 0.5F, 1F, 0.95F, 1F, 0.6F, 0.7F, 1F, 0.8F, 0.55F, 1F, 0.95F, 0.6F, 0F}));

    private SynthwavePalettes() {}

    public static boolean has(String sky) {
        return PALETTES.containsKey(sky);
    }

    static void apply(ShaderInstance shader, String sky) {
        float[] p = PALETTES.getOrDefault(sky, PALETTES.get("synthwave"));
        for (int i = 0; i < UNIFORMS.length; i++) {
            shader.safeGetUniform(UNIFORMS[i]).set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
        }
        shader.safeGetUniform("Moon").set(p[UNIFORMS.length * 3]);
    }
}
