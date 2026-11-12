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
    /**
     * The loaf's collar (the loaf model is the standing one with its blend shape on, vertex for vertex): the standing
     * collar carried onto the loaf by the fur under it, then laid on the loaf's fur round its neck - a loop of points
     * (x, y, z) and the way out from the fur at each; tucked into the fur at the back of the neck, where it'd be hidden.
     * (Measured from the models: the band's plane leans forward like the standing one's.)
     */
    private static final float[] LOAF_LOOP = {
            -9.44F, 23.17F, 15.01F, -9.23F, 23.99F, 14.07F, -8.71F, 24.79F, 13.13F, -7.86F, 25.47F, 12.30F,
            -6.85F, 26.01F, 11.63F, -5.90F, 26.43F, 11.09F, -5.11F, 26.82F, 10.59F, -4.45F, 27.32F, 9.98F,
            -3.83F, 28.11F, 9.04F, -3.12F, 29.30F, 7.65F, -2.06F, 30.56F, 6.15F, -0.54F, 31.42F, 5.08F,
            1.28F, 31.76F, 4.58F, 3.05F, 31.61F, 4.63F, 4.54F, 31.04F, 5.18F, 5.74F, 30.22F, 6.03F,
            6.76F, 29.34F, 6.95F, 7.62F, 28.43F, 7.92F, 8.32F, 27.46F, 8.98F, 8.92F, 26.45F, 10.09F,
            9.48F, 25.45F, 11.18F, 10.02F, 24.49F, 12.23F, 10.49F, 23.56F, 13.25F, 10.80F, 22.64F, 14.27F,
            10.93F, 21.78F, 15.23F, 10.94F, 21.00F, 16.11F, 10.84F, 20.30F, 16.91F, 10.58F, 19.58F, 17.75F,
            10.14F, 18.74F, 18.72F, 9.30F, 17.86F, 19.78F, 7.74F, 17.14F, 20.70F, 5.49F, 16.70F, 21.34F,
            3.04F, 16.46F, 21.78F, 0.84F, 16.32F, 22.09F, -1.01F, 16.32F, 22.21F, -2.60F, 16.46F, 22.15F,
            -3.96F, 16.69F, 21.98F, -5.12F, 17.02F, 21.69F, -6.08F, 17.42F, 21.30F, -6.85F, 17.83F, 20.89F,
            -7.44F, 18.20F, 20.50F, -7.93F, 18.58F, 20.11F, -8.39F, 19.04F, 19.62F, -8.82F, 19.62F, 18.99F,
            -9.15F, 20.27F, 18.27F, -9.34F, 20.93F, 17.54F, -9.42F, 21.62F, 16.77F, -9.46F, 22.37F, 15.92F};
    private static final float[] LOAF_OUT = {
            -1.00F, 0.03F, 0.04F, -0.99F, 0.12F, -0.07F, -0.96F, 0.22F, -0.18F, -0.90F, 0.31F, -0.30F,
            -0.82F, 0.40F, -0.40F, -0.74F, 0.47F, -0.48F, -0.65F, 0.52F, -0.55F, -0.55F, 0.57F, -0.61F,
            -0.45F, 0.61F, -0.66F, -0.32F, 0.64F, -0.70F, -0.18F, 0.66F, -0.73F, -0.03F, 0.66F, -0.75F,
            0.11F, 0.65F, -0.75F, 0.25F, 0.63F, -0.73F, 0.36F, 0.61F, -0.71F, 0.47F, 0.57F, -0.68F,
            0.57F, 0.52F, -0.63F, 0.67F, 0.47F, -0.58F, 0.76F, 0.41F, -0.51F, 0.85F, 0.32F, -0.42F,
            0.91F, 0.24F, -0.33F, 0.96F, 0.15F, -0.23F, 0.99F, 0.06F, -0.14F, 1.00F, -0.02F, -0.04F,
            0.99F, -0.10F, 0.05F, 0.98F, -0.17F, 0.13F, 0.96F, -0.22F, 0.19F, 0.93F, -0.28F, 0.25F,
            0.88F, -0.34F, 0.33F, 0.81F, -0.42F, 0.42F, 0.70F, -0.50F, 0.52F, 0.53F, -0.58F, 0.62F,
            0.30F, -0.64F, 0.71F, 0.09F, -0.66F, 0.74F, -0.09F, -0.66F, 0.75F, -0.24F, -0.63F, 0.73F,
            -0.37F, -0.60F, 0.71F, -0.48F, -0.57F, 0.67F, -0.57F, -0.52F, 0.63F, -0.65F, -0.48F, 0.59F,
            -0.70F, -0.45F, 0.56F, -0.74F, -0.42F, 0.52F, -0.79F, -0.38F, 0.49F, -0.85F, -0.32F, 0.42F,
            -0.90F, -0.26F, 0.35F, -0.94F, -0.20F, 0.29F, -0.96F, -0.14F, 0.22F, -0.99F, -0.06F, 0.13F};
    private static final float LOAF_NX = 0.044F, LOAF_NY = 0.749F, LOAF_NZ = 0.661F;
    /** The loop's point under the chin (where a name tag hangs). */
    private static final int LOAF_FRONT = 34;
    /** How far out from the fur, and how wide (model units). */
    private static final float OUT = 0.15F, HALF_WIDTH = 1.0F, THICK = 0.6F, SNUG = 0.95F;

    private OiiaCatCollar() {}

    /** Draws it with this pose (the cat's model, in model units): the loaf's ring if loaf, else the standing cat's. */
    static void draw(OiiaCatEntity cat, PoseStack pose, MultiBufferSource buffers, int light, boolean loaf) {
        if (!cat.isTame()) {
            return;
        }
        int colour = 0xFF000000 | cat.collar().getTextureDiffuseColor();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose p = pose.last();
        if (loaf) {
            drawLoaf(cat, p, vc, colour, light);
            return;
        }
        Ring ring = STANDING;
        int n = ring.radii.length;
        // (u: across the cat, x; v: along the ring's tilted plane; w: its normal)
        Vector3f w = new Vector3f(0, -ring.dz, ring.dy).negate(); // (forward-up)
        if (w.y < 0) {
            w.negate();
        }
        for (int i = 0; i < n; i++) {
            float a0 = -Mth.PI + i * Mth.TWO_PI / n, a1 = a0 + Mth.TWO_PI / n;
            float r0 = ring.radii[i] * SNUG + OUT, r1 = ring.radii[(i + 1) % n] * SNUG + OUT;
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
            Vector3f top = at(ring, a, ring.radii[at] * SNUG + OUT + 0.3F);
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

    /** The loaf's collar: a band along LOAF_LOOP, as wide as the standing one, its outside facing out from the fur. */
    private static void drawLoaf(OiiaCatEntity cat, PoseStack.Pose p, VertexConsumer vc, int colour, int light) {
        int n = LOAF_LOOP.length / 3;
        Vector3f w = new Vector3f(LOAF_NX, LOAF_NY, LOAF_NZ).normalize();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Vector3f o0 = loafOut(i), o1 = loafOut(j);
            Vector3f p0 = loafAt(i, o0, OUT), p1 = loafAt(j, o1, OUT), q0 = loafAt(i, o0, OUT - THICK), q1 = loafAt(j, o1, OUT - THICK);
            Vector3f out = new Vector3f(o0).add(o1).normalize();
            int shade = shade(colour, 0.85F + 0.15F * Math.max(0, out.y));
            quad(vc, p, off(p0, w, -HALF_WIDTH), off(p1, w, -HALF_WIDTH), off(p1, w, HALF_WIDTH), off(p0, w, HALF_WIDTH), shade, out, light);
            quad(vc, p, off(q0, w, HALF_WIDTH), off(q1, w, HALF_WIDTH), off(p1, w, HALF_WIDTH), off(p0, w, HALF_WIDTH), shade(colour, 0.95F), w, light);
            quad(vc, p, off(p0, w, -HALF_WIDTH), off(p1, w, -HALF_WIDTH), off(q1, w, -HALF_WIDTH), off(q0, w, -HALF_WIDTH), shade(colour, 0.7F),
                    new Vector3f(w).negate(), light);
        }
        if (cat.hasCustomName()) {
            // (its tag, hanging under the chin)
            Vector3f fwd = loafOut(LOAF_FRONT);
            Vector3f top = loafAt(LOAF_FRONT, fwd, OUT + 0.3F);
            float s = 1.6F;
            Vector3f c = new Vector3f(top).add(0, -s * 1.3F, 0).add(new Vector3f(fwd).mul(0.4F));
            Vector3f x = new Vector3f(s, 0, 0), y = new Vector3f(0, s, 0), face = new Vector3f(0, 0, 1);
            quad(vc, p, new Vector3f(c).sub(x).sub(y), new Vector3f(c).add(x).sub(y), new Vector3f(c).add(x).add(y), new Vector3f(c).sub(x).add(y), 0xFFE8C040, face, light);
            quad(vc, p, new Vector3f(top).add(-0.25F, 0, 0), new Vector3f(top).add(0.25F, 0, 0), new Vector3f(c).add(0.25F, s, 0),
                    new Vector3f(c).add(-0.25F, s, 0), 0xFFB09030, face, light);
        }
    }

    private static Vector3f loafOut(int i) {
        return new Vector3f(LOAF_OUT[i * 3], LOAF_OUT[i * 3 + 1], LOAF_OUT[i * 3 + 2]).normalize();
    }

    /** The loop's point i, moved out (or in) from the fur by d (the loop's at the fur, plus the band's usual standoff). */
    private static Vector3f loafAt(int i, Vector3f out, float d) {
        return new Vector3f(LOAF_LOOP[i * 3], LOAF_LOOP[i * 3 + 1], LOAF_LOOP[i * 3 + 2]).add(new Vector3f(out).mul(d - OUT));
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
