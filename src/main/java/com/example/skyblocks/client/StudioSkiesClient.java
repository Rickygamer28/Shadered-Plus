package com.example.skyblocks.client;

import net.minecraft.client.renderer.ShaderInstance;

/** Hands a studio sky's settings (StudioSkies) to its shader: P0 .., four at a time (StudioSpec). */
public final class StudioSkiesClient {
    public static void apply(ShaderInstance shader, float[] p) {
        for (int i = 0; i < com.example.skyblocks.StudioSpec.VECS; i++) {
            shader.safeGetUniform("P" + i).set(at(p, i * 4), at(p, i * 4 + 1), at(p, i * 4 + 2), at(p, i * 4 + 3));
        }
    }

    private static float at(float[] p, int i) {
        return i < p.length ? p[i] : 0.0F;
    }

    private StudioSkiesClient() {}
}
