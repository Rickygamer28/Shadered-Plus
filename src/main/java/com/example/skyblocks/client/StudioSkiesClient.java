package com.example.skyblocks.client;

import net.minecraft.client.renderer.ShaderInstance;

/** Hands a studio sky's settings (StudioSkies) to its shader: P0 .. P12, four at a time. */
public final class StudioSkiesClient {
    public static void apply(ShaderInstance shader, float[] p) {
        for (int i = 0; i < 13; i++) {
            shader.safeGetUniform("P" + i).set(p[i * 4], p[i * 4 + 1], p[i * 4 + 2], p[i * 4 + 3]);
        }
    }

    private StudioSkiesClient() {}
}
