package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws a shape filled with a sky (the window kind: the sky as seen through it), from a block entity's renderer:
 * quads as x y z u v per vertex, already posed (camera-relative). Makes sure that part of the sky is drawn first;
 * with Iris, hands it to the island pass (IrisIslandTarget) like the other skies.
 */
public final class SkyQuads {
    public static void draw(SkyblockType type, float[] d, int count, MultiBufferSource buffers) {
        RenderType sky = SkyLiquidRenderer.skyType(type);
        if (sky == null || count <= 0) {
            return;
        }
        var data = com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(type);
        if (com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
            SkyUsage.ensureFresh(data);
            float[] copy = java.util.Arrays.copyOf(d, count);
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(sky, v -> emit(v, copy, copy.length));
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < count; i += 5) {
            minX = Math.min(minX, d[i]);
            maxX = Math.max(maxX, d[i]);
            minY = Math.min(minY, d[i + 1]);
            maxY = Math.max(maxY, d[i + 1]);
            minZ = Math.min(minZ, d[i + 2]);
            maxZ = Math.max(maxZ, d[i + 2]);
        }
        SkyUsage.ensurePart(data, SkyUsage.rectOfBox(minX, minY, minZ, maxX, maxY, maxZ));
        MultiBufferSource.BufferSource now = DrawNow.now(buffers);
        SkyUsage.placeKnown = true;
        try {
            emit(now.getBuffer(sky), d, count);
            now.endBatch(sky);
        } finally {
            SkyUsage.placeKnown = false;
        }
    }

    private static void emit(VertexConsumer c, float[] d, int count) {
        for (int i = 0; i < count; i += 5) {
            c.addVertex(d[i], d[i + 1], d[i + 2]).setColor(255, 255, 255, 255).setUv(d[i + 3], d[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
        }
    }

    /** Adds a quad (corners a b c d, each x y z, posed) both ways round; returns the new count. */
    public static int quad(float[] out, int n, float[] a, float[] b, float[] c, float[] e) {
        float[][] q = {a, b, c, e};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                int kk = pass == 0 ? k : 3 - k;
                out[n++] = q[kk][0];
                out[n++] = q[kk][1];
                out[n++] = q[kk][2];
                out[n++] = kk == 1 || kk == 2 ? 1 : 0;
                out[n++] = kk >= 2 ? 1 : 0;
            }
        }
        return n;
    }

    private SkyQuads() {}
}
