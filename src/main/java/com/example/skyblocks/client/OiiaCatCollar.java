package com.example.skyblocks.client;

import com.example.skyblocks.OiiaCatEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The cat companion's collar, in its dye colour, round its neck - a band hugging the model where the head meets the
 * body (fitted to the model's shape there: tools/models/oiia_cat measurements, in model units), and with a little gold
 * tag at the front when it has a name.
 */
final class OiiaCatCollar {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "textures/misc/white.png");

    /** A ring: its centre (x, y, z), the way round it goes forward-down (z, y), and its radius every 15 degrees from -180. */
    private record Ring(float cx, float cy, float cz, float dz, float dy, float[] radii) {}

    private static final Ring STANDING = new Ring(1.59F, 22.45F, 13.0F, 0.793F, -0.609F, new float[] {10.5F, 10.4F, 10.1F, 10.7F, 11.8F, 12.3F,
            11.5F, 9.6F, 9.5F, 9.7F, 10.2F, 10.5F, 10.4F, 10.8F, 11.7F, 12.1F, 10.9F, 9.9F, 10.0F, 9.6F, 9.6F, 10.1F, 10.4F, 10.4F});
    private static final Ring LOAF = new Ring(0.14F, 24.75F, 14.25F, 0.810F, -0.587F, new float[] {8.9F, 8.4F, 7.8F, 7.3F, 8.4F, 12.2F, 13.5F,
            12.0F, 10.9F, 9.0F, 8.5F, 8.5F, 9.4F, 10.1F, 10.9F, 10.8F, 10.1F, 9.8F, 9.9F, 10.4F, 10.6F, 10.5F, 10.0F, 9.4F});
    /** How far out from the fur, and how wide (model units). */
    private static final float OUT = 0.55F, HALF_WIDTH = 1.15F, THICK = 0.7F;

    private OiiaCatCollar() {}

    /** Draws it with this pose (the cat's model, in model units): the loaf's ring if loaf, else the standing cat's. */
    static void draw(OiiaCatEntity cat, PoseStack pose, MultiBufferSource buffers, int light, boolean loaf) {
        if (!cat.isTame()) {
            return;
        }
        Ring ring = loaf ? LOAF : STANDING;
        int colour = 0xFF000000 | cat.collar().getTextureDiffuseColor();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose p = pose.last();
        int n = ring.radii.length;
        // (u: across the cat, x; v: along the ring's tilted plane; w: its normal)
        Vector3f w = new Vector3f(0, -ring.dz, ring.dy).negate(); // (forward-up)
        if (w.y < 0) {
            w.negate();
        }
        for (int i = 0; i < n; i++) {
            float a0 = -Mth.PI + i * Mth.TWO_PI / n, a1 = a0 + Mth.TWO_PI / n;
            float r0 = ring.radii[i] + OUT, r1 = ring.radii[(i + 1) % n] + OUT;
            Vector3f p0 = at(ring, a0, r0), p1 = at(ring, a1, r1), q0 = at(ring, a0, r0 - THICK), q1 = at(ring, a1, r1 - THICK);
            Vector3f out = new Vector3f(p0).add(p1).mul(0.5F).sub(ring.cx, ring.cy, ring.cz).normalize();
            int shade = shade(colour, 0.85F + 0.15F * Math.max(0, out.y));
            // the band's outside, and its two edges
            quad(vc, p, off(p0, w, -HALF_WIDTH), off(p1, w, -HALF_WIDTH), off(p1, w, HALF_WIDTH), off(p0, w, HALF_WIDTH), shade, out, light);
            quad(vc, p, off(q0, w, HALF_WIDTH), off(q1, w, HALF_WIDTH), off(p1, w, HALF_WIDTH), off(p0, w, HALF_WIDTH), shade(colour, 0.95F), w, light);
            quad(vc, p, off(p0, w, -HALF_WIDTH), off(p1, w, -HALF_WIDTH), off(q1, w, -HALF_WIDTH), off(q0, w, -HALF_WIDTH), shade(colour, 0.7F),
                    new Vector3f(w).negate(), light);
        }
        if (cat.hasCustomName()) {
            // a little gold tag hanging at the front, under the chin
            float a = Mth.HALF_PI;
            int at = Math.round((a + Mth.PI) / (Mth.TWO_PI / n)) % n;
            Vector3f top = at(ring, a, ring.radii[at] + OUT + 0.3F);
            Vector3f fwd = new Vector3f(0, ring.dy, ring.dz).normalize(), down = new Vector3f(0, -1, 0);
            float s = 1.6F;
            Vector3f c = new Vector3f(top).add(new Vector3f(down).mul(s * 1.3F)).add(new Vector3f(fwd).mul(0.4F));
            int gold = 0xFFE8C040;
            Vector3f x = new Vector3f(s, 0, 0), y = new Vector3f(0, s, 0);
            Vector3f face = new Vector3f(0, 0, 1);
            quad(vc, p, new Vector3f(c).sub(x).sub(y), new Vector3f(c).add(x).sub(y), new Vector3f(c).add(x).add(y), new Vector3f(c).sub(x).add(y), gold, face, light);
            quad(vc, p, new Vector3f(top).add(-0.25F, 0, 0), new Vector3f(top).add(0.25F, 0, 0), new Vector3f(c).add(0.25F, s, 0),
                    new Vector3f(c).add(-0.25F, s, 0), 0xFFB09030, face, light);
        }
    }

    private static Vector3f at(Ring ring, float angle, float r) {
        float u = Mth.cos(angle) * r, v = Mth.sin(angle) * r;
        return new Vector3f(ring.cx + u, ring.cy + v * ring.dy, ring.cz + v * ring.dz);
    }

    private static Vector3f off(Vector3f p, Vector3f w, float by) {
        return new Vector3f(p).add(w.x * by, w.y * by, w.z * by);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int colour, Vector3f n, int light) {
        for (Vector3f v : new Vector3f[] {a, b, c, d}) {
            vc.addVertex(p, v.x, v.y, v.z).setColor(colour).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, n.x, n.y, n.z);
        }
    }

    private static int shade(int c, float f) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * f)), g = Math.min(255, (int) (((c >> 8) & 255) * f)), b = Math.min(255, (int) ((c & 255) * f));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
