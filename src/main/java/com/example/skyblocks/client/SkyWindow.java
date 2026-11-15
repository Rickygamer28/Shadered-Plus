package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.noodlegamer76.shadered.client.util.ModRenderTypes;
import com.noodlegamer76.shadered.client.util.SkyblockType;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Windows into a sky, as Shadered's sky blocks are (its render type for that sky: the sky's picture at each pixel of
 * the screen), in any shape: a renderer's quads (corner positions, through its pose), both ways round - with a shader
 * pack on, kept for the end of the frame (client/iris/IrisIslandTarget), as the sky liquids are.
 */
public final class SkyWindow {
    private SkyWindow() {}

    private static final Map<String, RenderType> TYPES = new HashMap<>();
    private static final Map<RenderType, SkyblockType> SKIES = new HashMap<>();

    /** Shadered's render type for a sky and pass (by name); null if there's no such sky here. */
    @Nullable
    public static RenderType type(String sky, String pass) {
        String key = sky + "/" + pass;
        if (TYPES.containsKey(key)) {
            return TYPES.get(key);
        }
        RenderType type;
        try {
            SkyblockPass p;
            try {
                p = SkyblockPass.valueOf(pass);
            } catch (IllegalArgumentException e) {
                p = SkyblockPass.NORMAL;
            }
            SkyblockType t = SkyblockType.valueOf(sky);
            type = ModRenderTypes.getSkyblockRenderType(t, p);
            if (type != null) {
                SKIES.put(type, t);
            }
        } catch (RuntimeException e) {
            type = null;
        }
        TYPES.put(key, type);
        return type;
    }

    /** Quads of sky: in `quads`, each quad's four corners (x, y, z, 12 numbers a quad), placed by `pose`. */
    public static void draw(MultiBufferSource buffers, RenderType type, Matrix4f pose, float[] quads, int count) {
        if (count <= 0) {
            return;
        }
        // (its sky's picture brought up to date first: a sky nothing else on screen shows isn't drawn every frame)
        SkyblockType sky = SKIES.get(type);
        if (sky != null) {
            try {
                SkyUsage.ensureFresh(com.noodlegamer76.shadered.client.renderer.SkyblockRenderer.getData(sky));
            } catch (RuntimeException ignored) {
                // (drawn as it is)
            }
        }
        if (com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
            Matrix4f copy = new Matrix4f(pose);
            float[] q = java.util.Arrays.copyOf(quads, count * 12);
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(type, c -> emit(c, copy, q, count));
            return;
        }
        emit(buffers.getBuffer(type), pose, quads, count);
    }

    private static void emit(VertexConsumer c, Matrix4f m, float[] q, int count) {
        for (int i = 0; i < count; i++) {
            int o = i * 12;
            vertex(c, m, q[o], q[o + 1], q[o + 2]);
            vertex(c, m, q[o + 3], q[o + 4], q[o + 5]);
            vertex(c, m, q[o + 6], q[o + 7], q[o + 8]);
            vertex(c, m, q[o + 9], q[o + 10], q[o + 11]);
            // (and the other way round: seen from either side)
            vertex(c, m, q[o + 9], q[o + 10], q[o + 11]);
            vertex(c, m, q[o + 6], q[o + 7], q[o + 8]);
            vertex(c, m, q[o + 3], q[o + 4], q[o + 5]);
            vertex(c, m, q[o], q[o + 1], q[o + 2]);
        }
    }

    private static void vertex(VertexConsumer c, Matrix4f m, float x, float y, float z) {
        c.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** A growing list of quads (4 corners, x y z each). */
    public static final class Quads {
        public float[] data = new float[12 * 64];
        public int count;

        public void add(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
            if ((count + 1) * 12 > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            int o = count * 12;
            data[o] = ax;
            data[o + 1] = ay;
            data[o + 2] = az;
            data[o + 3] = bx;
            data[o + 4] = by;
            data[o + 5] = bz;
            data[o + 6] = cx;
            data[o + 7] = cy;
            data[o + 8] = cz;
            data[o + 9] = dx;
            data[o + 10] = dy;
            data[o + 11] = dz;
            count++;
        }

        public void clear() {
            count = 0;
        }
    }
}
