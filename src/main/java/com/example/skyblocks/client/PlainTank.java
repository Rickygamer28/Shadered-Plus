package com.example.skyblocks.client;

import com.example.skyblocks.WhirlpoolBlock;
import com.example.skyblocks.WhirlpoolBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The whirlpool tank's plain look: what it holds settled in layers of their own pictures, alive - the layers sliding to
 * new heights as it fills and drains, bubbles rising up the glass (quick in hot fluids, slow in thick ones), hot ones
 * glowing up the glass and throwing embers, light rippling under a clear one's surface and a bright line where it meets
 * the glass; brass level marks at a quarter, half and three quarters, and a notch at each layer's top; whatever's piped
 * in falling from the roof in a stream of its own (each fluid its own spot) and splashing in; and - looked at - each
 * layer's name and amount floating beside it.
 */
final class PlainTank {
    private PlainTank() {}

    private static final int BRASS = 0xFFE8B45A, BRASS_DARK = 0xFF6A4410;

    private record Layer(WhirlpoolRenderer.Look look, float y0, float y1) {}

    static void draw(WhirlpoolBlockEntity be, List<WhirlpoolRenderer.Look> looks, PoseStack poseStack, MultiBufferSource buffers, int light, float t,
                     float lo, float hi, int r) {
        if (looks.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        float dt = be.lastFrame == 0 ? 0.0F : Math.min(0.1F, (now - be.lastFrame) / 1.0e9F);
        be.lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        TextureAtlasSprite white = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WhirlpoolRenderer.WHITE);
        PoseStack.Pose pose = poseStack.last();
        // ---- the layers: liquids up from the floor (the densest lowest), gases down from the roof, sliding to their heights
        float filled = (hi - lo) * Math.min(1.0F, Math.max(be.shownFull, 0.0F));
        List<WhirlpoolRenderer.Look> liquids = new ArrayList<>(), gases = new ArrayList<>();
        for (WhirlpoolRenderer.Look l : looks) {
            (l.gas() ? gases : liquids).add(l);
        }
        liquids.sort((a, b) -> Integer.compare(b.density(), a.density()));
        gases.sort((a, b) -> Integer.compare(a.density(), b.density()));
        boolean first = be.shownShare.isEmpty();
        java.util.Set<Fluid> present = new java.util.HashSet<>();
        for (WhirlpoolRenderer.Look l : looks) {
            Fluid f = l.stack().getFluid();
            present.add(f);
            float s = be.shownShare.getOrDefault(f, first ? l.share() : 0.0F);
            be.shownShare.put(f, s + (l.share() - s) * Math.min(1.0F, dt * 2.5F));
        }
        be.shownShare.keySet().retainAll(present);
        List<Layer> layers = new ArrayList<>();
        float y = lo;
        for (WhirlpoolRenderer.Look l : liquids) {
            float top = Math.min(hi, y + filled * be.shownShare.get(l.stack().getFluid()));
            if (top - y > 0.004F) {
                layers.add(new Layer(l, y, top));
            }
            y = top;
        }
        float liquidTop = y;
        List<Layer> gasLayers = new ArrayList<>();
        float roof = hi;
        for (WhirlpoolRenderer.Look l : gases) {
            float under = Math.max(liquidTop, roof - filled * be.shownShare.get(l.stack().getFluid()));
            if (roof - under > 0.004F) {
                gasLayers.add(new Layer(l, under, roof));
            }
            roof = under;
        }
        float since = (System.currentTimeMillis() - be.rippleAt) / 1000.0F;
        float ripple = (0.012F + 0.05F * Math.max(0.0F, 1.0F - since / 2.5F)) * Math.min(1.0F, (hi - lo) / 3.0F);

        VertexConsumer v = buffers.getBuffer(WhirlpoolRenderer.Types.POOL);
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            WhirlpoolRenderer.Look l = layer.look;
            int lit = l.glows() ? LightTexture.FULL_BRIGHT : light;
            float k = 0.78F + 0.22F * (layer.y1 - lo) / Math.max(0.01F, hi - lo);
            int argb = WhirlpoolRenderer.shade((l.argb() & 0x00FFFFFF) | (0xEE << 24), k);
            if (i == 0) {
                WhirlpoolRenderer.floorQuad(v, pose, l, lo, hi, WhirlpoolRenderer.shade(argb, 0.7F), lit);
            }
            WhirlpoolRenderer.sides(v, pose, l, lo, hi, layer.y0, layer.y1, argb, lit);
            if (i < layers.size() - 1) {
                WhirlpoolRenderer.sides(v, pose, l, lo - 0.001F, hi + 0.001F, layer.y1 - 0.02F, layer.y1, WhirlpoolRenderer.shade(argb, 0.55F), lit);
            } else {
                surface(v, pose, l, lo, hi, layer.y1, t, ripple, argb, lit);
            }
            bubbles(v, pose, white, l, lo, hi, layer.y0, layer.y1, t, i, lit);
        }
        for (int i = 0; i < gasLayers.size(); i++) {
            Layer layer = gasLayers.get(i);
            WhirlpoolRenderer.Look l = layer.look;
            int lit = l.glows() ? LightTexture.FULL_BRIGHT : light;
            // (drifting: each block's column of it thicker and thinner in turn)
            for (float a = lo; a < hi - 0.001F; a = Math.min(hi, (float) Math.floor(a) + 1)) {
                float b = Math.min(hi, (float) Math.floor(a) + 1);
                int alpha = (int) (0x58 + 0x28 * Mth.sin(t * 0.8F + a * 1.7F + i));
                int c = (l.argb() & 0x00FFFFFF) | (alpha << 24);
                WhirlpoolRenderer.column(v, pose, l, a, lo, b, lo, layer.y0, layer.y1, c, lit);
                WhirlpoolRenderer.column(v, pose, l, b, hi, a, hi, layer.y0, layer.y1, c, lit);
                WhirlpoolRenderer.column(v, pose, l, lo, b, lo, a, layer.y0, layer.y1, c, lit);
                WhirlpoolRenderer.column(v, pose, l, hi, a, hi, b, layer.y0, layer.y1, c, lit);
            }
            WhirlpoolRenderer.floorQuad(v, pose, l, lo, hi, layer.y0, (l.argb() & 0x00FFFFFF) | (0x48 << 24), lit);
        }
        marks(v, pose, white, lo, hi, layers, light);
        List<float[]> splashes = streams(be, v, pose, white, looks, lo, hi, liquidTop, t, light);

        // ---- light: hot fluids glowing up the glass, light rippling under a clear surface, the surface's bright line
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.pose();
        if (!layers.isEmpty()) {
            Layer topLayer = layers.get(layers.size() - 1);
            WhirlpoolRenderer.Look l = topLayer.look;
            int c = l.mean();
            float cr = (c >> 16 & 255) / 255.0F, cg = (c >> 8 & 255) / 255.0F, cb = (c & 255) / 255.0F;
            if (l.hot()) {
                float pulse = 0.28F + 0.06F * Mth.sin(t * 3.0F);
                walls(glow, m, lo, hi, topLayer.y1, Math.min(hi, topLayer.y1 + 0.8F), 1.0F, 0.55F, 0.18F, pulse, 0.0F);
            } else if (!l.glows()) {
                caustics(glow, m, lo, hi, Math.max(topLayer.y0, topLayer.y1 - 0.6F), topLayer.y1, t);
            }
            // the bright line where it meets the glass
            walls(glow, m, lo - 0.002F, hi + 0.002F, topLayer.y1 - 0.012F, topLayer.y1 + 0.02F, Math.min(1, cr + 0.4F), Math.min(1, cg + 0.4F),
                    Math.min(1, cb + 0.4F), 0.5F, 1.0F);
            for (float[] s : splashes) { // (rings spreading where a stream lands)
                float ring = (t * 1.6F + s[2]) % 1.0F;
                ringQuads(glow, m, s[0], topLayer.y1 + 0.01F, s[1], 0.12F + ring * 0.45F, 0.9F, 0.95F, 1.0F, 0.5F * (1.0F - ring));
            }
        }
        // ---- the world: embers off hot surfaces, splashes where streams land
        Level level = be.getLevel();
        if (level != null && !layers.isEmpty() && dt > 0) {
            BlockPos bp = be.getBlockPos();
            Layer topLayer = layers.get(layers.size() - 1);
            if (topLayer.look.hot() && level.random.nextFloat() < dt * 2.0F * (hi - lo)) {
                level.addParticle(ParticleTypes.LAVA, bp.getX() + Mth.lerp(level.random.nextFloat(), lo, hi), bp.getY() + topLayer.y1 + 0.05,
                        bp.getZ() + Mth.lerp(level.random.nextFloat(), lo, hi), 0, 0, 0);
            }
            for (float[] s : splashes) {
                if (level.random.nextFloat() < dt * 8.0F) {
                    level.addParticle(topLayer.look.hot() ? ParticleTypes.LAVA : ParticleTypes.SPLASH, bp.getX() + s[0], bp.getY() + topLayer.y1 + 0.05,
                            bp.getZ() + s[1], (level.random.nextFloat() - 0.5F) * 0.2F, 0.15F, (level.random.nextFloat() - 0.5F) * 0.2F);
                }
            }
        }
        // ---- a label plate for each layer on its glass (last: text ends the buffers above)
        labels(be, poseStack, buffers, layers, gasLayers, lo, hi, r, light);
    }

    /** The top layer's surface: its own picture, moving gently. */
    private static void surface(VertexConsumer v, PoseStack.Pose pose, WhirlpoolRenderer.Look l, float lo, float hi, float top, float t, float ripple,
                                int argb, int lit) {
        int sa = (argb & 0x00FFFFFF) | (0xE6 << 24);
        for (float a = lo; a < hi - 0.001F; a = Math.min(hi, (float) Math.floor(a * 2) / 2 + 0.5F)) {
            float b = Math.min(hi, (float) Math.floor(a * 2) / 2 + 0.5F);
            for (float c = lo; c < hi - 0.001F; c = Math.min(hi, (float) Math.floor(c * 2) / 2 + 0.5F)) {
                float d = Math.min(hi, (float) Math.floor(c * 2) / 2 + 0.5F);
                float u0 = WhirlpoolRenderer.frac(a), u1 = u0 + (b - a), v0 = WhirlpoolRenderer.frac(c), v1 = v0 + (d - c);
                WhirlpoolRenderer.vertex(v, pose, a, top + WhirlpoolRenderer.wave(a, c, t, ripple), c, l.sprite(), u0, v0, sa, lit);
                WhirlpoolRenderer.vertex(v, pose, a, top + WhirlpoolRenderer.wave(a, d, t, ripple), d, l.sprite(), u0, v1, sa, lit);
                WhirlpoolRenderer.vertex(v, pose, b, top + WhirlpoolRenderer.wave(b, d, t, ripple), d, l.sprite(), u1, v1, sa, lit);
                WhirlpoolRenderer.vertex(v, pose, b, top + WhirlpoolRenderer.wave(b, c, t, ripple), c, l.sprite(), u1, v0, sa, lit);
            }
        }
    }

    /** A flat quad on a wall: wall w (0 -x, 1 +x, 2 -z, 3 +z) at its plane p, from a to b along it, y0 to y1. */
    private static void wallQuad(VertexConsumer v, PoseStack.Pose pose, TextureAtlasSprite white, int w, float p, float a, float b, float y0, float y1,
                                 int argb, int lit) {
        float h = 0.5F;
        if (w < 2) {
            WhirlpoolRenderer.vertex(v, pose, p, y1, a, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, p, y0, a, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, p, y0, b, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, p, y1, b, white, h, h, argb, lit);
        } else {
            WhirlpoolRenderer.vertex(v, pose, a, y1, p, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, a, y0, p, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, b, y0, p, white, h, h, argb, lit);
            WhirlpoolRenderer.vertex(v, pose, b, y1, p, white, h, h, argb, lit);
        }
    }

    private static float plane(int w, float lo, float hi, float out) {
        return w % 2 == 0 ? lo - out : hi + out;
    }

    /** Bubbles rising up the glass in a layer: quick in hot fluids, slow in thick ones; growing and fading at its top. */
    private static void bubbles(VertexConsumer v, PoseStack.Pose pose, TextureAtlasSprite white, WhirlpoolRenderer.Look l, float lo, float hi, float y0,
                                float y1, float t, int layer, int lit) {
        float h = y1 - y0;
        if (h < 0.15F) {
            return;
        }
        float speed = (l.hot() ? 0.9F : 0.4F) / (float) Math.sqrt(Math.max(1.0, l.viscosity() / 1000.0)) / Math.max(0.5F, h);
        int per = Mth.clamp(Math.round((hi - lo) * 1.5F), 3, 12);
        for (int w = 0; w < 4; w++) {
            float p = plane(w, lo, hi, 0.004F);
            for (int i = 0; i < per; i++) {
                int seed = i * 7919 + w * 104729 + layer * 1299709;
                float along = lo + 0.1F + (hi - lo - 0.2F) * hash(seed), phase = hash(seed + 1), size = 0.035F + 0.03F * hash(seed + 2);
                float f = (phase + t * speed * (0.7F + 0.6F * hash(seed + 3))) % 1.0F;
                float by = y0 + 0.05F + f * (h - 0.1F), wob = 0.02F * Mth.sin(t * 3.0F + seed);
                float s = f > 0.9F ? size * (1.0F + (f - 0.9F) * 8.0F) : size;
                int a = f > 0.9F ? (int) (90 * (1.0F - (f - 0.9F) * 10.0F)) : 90;
                wallQuad(v, pose, white, w, p, along + wob - s, along + wob + s, by - s, by + s, (a << 24) | 0xFFFFFF, lit);
                wallQuad(v, pose, white, w, plane(w, lo, hi, 0.005F), along + wob - s * 0.75F, along + wob - s * 0.25F, by + s * 0.25F, by + s * 0.75F,
                        (Math.min(255, a * 2) << 24) | 0xFFFFFF, lit);
            }
        }
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }

    /** Brass level marks on each wall (a quarter, half, three quarters up) and a white notch at each layer's top. */
    private static void marks(VertexConsumer v, PoseStack.Pose pose, TextureAtlasSprite white, float lo, float hi, List<Layer> layers, int light) {
        float span = hi - lo;
        for (int w = 0; w < 4; w++) {
            float p = plane(w, lo, hi, 0.006F);
            float a = lo + 0.08F;
            for (float f : new float[] {0.25F, 0.5F, 0.75F}) {
                float y = lo + span * f;
                wallQuad(v, pose, white, w, p, a, a + (f == 0.5F ? 0.42F : 0.28F), y - 0.025F, y + 0.025F, BRASS, light);
                wallQuad(v, pose, white, w, p, a, a + (f == 0.5F ? 0.42F : 0.28F), y - 0.04F, y - 0.025F, BRASS_DARK, light);
            }
            for (Layer layer : layers) {
                float b = hi - 0.08F;
                wallQuad(v, pose, white, w, p, b - 0.16F, b, layer.y1 - 0.018F, layer.y1 + 0.018F, 0xE0FFFFFF, light);
            }
        }
    }

    /**
     * What's being piped in: a stream of each fluid falling from the roof to the surface, each at a spot of its own
     * (so several fluids at once fall side by side). Returns where they land, for their splashes.
     */
    private static List<float[]> streams(WhirlpoolBlockEntity be, VertexConsumer v, PoseStack.Pose pose, TextureAtlasSprite white,
                                         List<WhirlpoolRenderer.Look> looks, float lo, float hi, float surface, float t, int light) {
        List<float[]> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<WhirlpoolRenderer.Look> pouring = new ArrayList<>();
        for (WhirlpoolRenderer.Look l : looks) {
            Long at = be.pouredAt.get(l.stack().getFluid());
            if (at != null && now - at < 1200 && !l.gas()) {
                pouring.add(l);
            }
        }
        pouring.sort(Comparator.comparing(l -> BuiltInRegistries.FLUID.getKey(l.stack().getFluid()).toString()));
        float span = hi - lo, top = hi - 0.02F;
        for (int i = 0; i < pouring.size() && i < 6; i++) {
            WhirlpoolRenderer.Look l = pouring.get(i);
            float x = lo + span * (0.25F + 0.5F * ((i * 0.618F + 0.2F) % 1.0F)), z = lo + span * (0.3F + 0.4F * ((i * 0.382F + 0.5F) % 1.0F));
            if (top - surface < 0.05F) {
                continue;
            }
            int lit = l.glows() ? LightTexture.FULL_BRIGHT : light;
            int argb = (l.argb() & 0x00FFFFFF) | (0xF0 << 24);
            float w = 0.08F;
            // (its picture running down it, a little at a time)
            for (float y0 = surface; y0 < top - 0.001F; y0 += 0.25F) {
                float y1 = Math.min(top, y0 + 0.25F), vv = WhirlpoolRenderer.frac(y0 * 0.7F + t * 2.0F) * 0.7F;
                float vh = vv + (y1 - y0) * 0.3F;
                for (int s = 0; s < 4; s++) {
                    float ax = s == 0 ? x - w : s == 1 ? x + w : x - w, az = s == 2 ? z - w : s == 3 ? z + w : z - w;
                    float bx = s == 0 ? x - w : s == 1 ? x + w : x + w, bz = s == 2 ? z - w : s == 3 ? z + w : z + w;
                    WhirlpoolRenderer.vertex(v, pose, ax, y1, az, l.sprite(), 0.3F, vv, argb, lit);
                    WhirlpoolRenderer.vertex(v, pose, ax, y0, az, l.sprite(), 0.3F, vh, argb, lit);
                    WhirlpoolRenderer.vertex(v, pose, bx, y0, bz, l.sprite(), 0.5F, vh, argb, lit);
                    WhirlpoolRenderer.vertex(v, pose, bx, y1, bz, l.sprite(), 0.5F, vv, argb, lit);
                }
            }
            out.add(new float[] {x, z, i * 0.37F});
        }
        return out;
    }

    /** A quad seen from both sides (the light layers draw only the side facing you). */
    private static void quad(VertexConsumer v, Matrix4f m, float[] p, float r, float g, float b, float a0, float a1) {
        float[] al = {a0, a1, a1, a0};
        for (int i = 0; i < 4; i++) {
            v.addVertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(r, g, b, al[i]);
        }
        for (int i = 3; i >= 0; i--) {
            v.addVertex(m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(r, g, b, al[i]);
        }
    }

    /** A quad on wall w's plane p: along it from a to b, up it from ya0/yb0 to ya1/yb1 (alpha a0 at its foot, a1 at its top). */
    private static void onWall(VertexConsumer v, Matrix4f m, int w, float p, float a, float b, float ya0, float ya1, float yb0, float yb1, float r, float g,
                               float bl, float a0, float a1) {
        float[] q = w < 2 ? new float[] {p, ya0, a, p, ya1, a, p, yb1, b, p, yb0, b} : new float[] {a, ya0, p, a, ya1, p, b, yb1, p, b, yb0, p};
        quad(v, m, q, r, g, bl, a0, a1);
    }

    /** Glow up the four walls from y0 to y1 (fading out upwards when fade is 0, even when it's 1). */
    private static void walls(VertexConsumer v, Matrix4f m, float lo, float hi, float y0, float y1, float r, float g, float b, float a, float fade) {
        for (int w = 0; w < 4; w++) {
            onWall(v, m, w, w % 2 == 0 ? lo - 0.002F : hi + 0.002F, lo, hi, y0, y1, y0, y1, r, g, b, a, a * fade);
        }
    }

    /** Light rippling just under a clear surface, on the walls: wavy bright threads, moving. */
    private static void caustics(VertexConsumer v, Matrix4f m, float lo, float hi, float y0, float y1, float t) {
        float h = y1 - y0;
        if (h < 0.1F) {
            return;
        }
        for (int w = 0; w < 4; w++) {
            float p = w % 2 == 0 ? lo - 0.003F : hi + 0.003F;
            for (int k = 0; k < 4; k++) {
                float base = y1 - 0.06F - k * h / 4.5F, th = 0.012F, al = 0.22F * (1.0F - k / 4.5F);
                for (float a = lo; a < hi - 0.01F; a += 0.125F) {
                    float b = Math.min(hi, a + 0.125F);
                    float ya = base + 0.03F * Mth.sin(a * 6.0F + t * 2.0F + k * 1.7F + w), yb = base + 0.03F * Mth.sin(b * 6.0F + t * 2.0F + k * 1.7F + w);
                    onWall(v, m, w, p, a, b, ya - th, ya + th, yb - th, yb + th, 0.65F, 0.88F, 1.0F, al, al);
                }
            }
        }
    }

    /** A flat ring on the surface (radius r), for a splash. */
    private static void ringQuads(VertexConsumer v, Matrix4f m, float x, float y, float z, float r, float cr, float cg, float cb, float a) {
        int n = 20;
        float th = 0.025F;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            quad(v, m, new float[] {x + Mth.cos(a0) * (r - th), y, z + Mth.sin(a0) * (r - th), x + Mth.cos(a0) * (r + th), y, z + Mth.sin(a0) * (r + th),
                    x + Mth.cos(a1) * (r + th), y, z + Mth.sin(a1) * (r + th), x + Mth.cos(a1) * (r - th), y, z + Mth.sin(a1) * (r - th)}, cr, cg, cb, a, a);
        }
    }

    /**
     * A label plate for each layer on the glass of each of its four sides: a small dark plate in a brass rim, stuck to the
     * glass at the layer's height, a stripe of its colour, its name and amount printed on it.
     */
    private static void labels(WhirlpoolBlockEntity be, PoseStack poseStack, MultiBufferSource buffers, List<Layer> layers, List<Layer> gases, float lo,
                               float hi, int r, int light) {
        Minecraft mc = Minecraft.getInstance();
        List<Layer> all = new ArrayList<>(layers);
        all.addAll(gases);
        all.sort(Comparator.comparingDouble(l -> l.y0));
        Font font = mc.font;
        float scale = 0.0105F;
        // (spaced so they don't overlap, kept inside the tank's height)
        float[] ys = new float[all.size()];
        float last = -999;
        for (int i = 0; i < all.size(); i++) {
            ys[i] = Math.max((all.get(i).y0 + all.get(i).y1) / 2.0F, last + 0.16F);
            last = ys[i];
        }
        float over = last - (hi - 0.1F);
        if (over > 0) {
            for (int i = 0; i < ys.length; i++) {
                ys[i] -= over;
            }
        }
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < all.size(); i++) {
                Layer layer = all.get(i);
                String name = layer.look.stack().getHoverName().getString(), amount = WhirlpoolBlock.buckets(layer.look.amount());
                int tw = font.width(name) + 6 + font.width(amount);
                float pw = tw + 12, ph = 12;
                poseStack.pushPose();
                // (out on the glass of that side, its middle)
                poseStack.translate(0.5F, ys[i], 0.5F);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(side * 90.0F));
                poseStack.translate(0.0F, 0.0F, r + 0.5F + 0.012F);
                poseStack.scale(scale, -scale, scale);
                Matrix4f m = poseStack.last().pose();
                VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
                float x0 = -pw / 2.0F, x1 = pw / 2.0F, y0 = -ph / 2.0F, y1 = ph / 2.0F;
                rect(bg, m, x0 - 1, y0 - 1, x1 + 1, y1 + 1, -0.4F, BRASS_DARK, light);
                rect(bg, m, x0, y0, x1, y1, -0.3F, BRASS, light);
                rect(bg, m, x0 + 1, y0 + 1, x1 - 1, y1 - 1, -0.2F, 0xFF161A24, light);
                rect(bg, m, x0 + 1, y0 + 1, x0 + 3, y1 - 1, -0.1F, 0xFF000000 | layer.look.mean(), light);
                font.drawInBatch(name, x0 + 6, -4, 0xFFE6EDF7, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
                font.drawInBatch(amount, x1 - 4 - font.width(amount), -4, 0xFFAAB4C8, false, m, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
                poseStack.popPose();
            }
        }
    }

    /** A flat coloured rectangle (a label's plate) at depth z (more negative: nearer the glass), seen from both sides. */
    private static void rect(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int light) {
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        v.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
    }
}
