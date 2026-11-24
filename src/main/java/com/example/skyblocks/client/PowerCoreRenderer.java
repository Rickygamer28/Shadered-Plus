package com.example.skyblocks.client;

import com.example.skyblocks.PowerCoreBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A power core (PowerCoreBlockEntity): what it holds, in its skin, in 3D - a box (just inside its frame alone; filling its
 * core chamber in one) traced through by shaders/core/energy_core - easing to how full it is; and
 * its light, coloured (ShaderLightRenderer: the star's warm, the storm's pale violet, the plasma's purple, the atom's blue).
 */
public class PowerCoreRenderer implements BlockEntityRenderer<PowerCoreBlockEntity> {
    static ShaderInstance shader;
    /** Each skin's light. */
    private static final int[] LIGHT = {0xFFA040, 0x9A9CFF, 0xC070FF, 0x60C8FF};

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "energy_core"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CORE = create("skyblocks_power_core", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    /**
     * The boxes to draw this frame (x, y, z from the camera; u, v; colour) - drawn after the coloured lights have been laid
     * over the picture (LatePass): laid over it, they'd tint what's in front by what's behind (a glowing star showing the
     * frames behind it as a stripe of its own colour).
     */
    private static final java.util.List<float[]> VERTS = new java.util.ArrayList<>();
    private static boolean queued;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class Frames {
        private Frames() {}

        @SubscribeEvent
        public static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_SKY && !MirrorRenderer.isRenderingReflection()) {
                VERTS.clear();
                queued = false;
            }
        }
    }

    private static void drawAll(MultiBufferSource.BufferSource source) {
        queued = false;
        if (VERTS.isEmpty() || shader == null) {
            VERTS.clear();
            return;
        }
        VertexConsumer vc = source.getBuffer(Types.CORE);
        Matrix4f identity = new Matrix4f();
        for (float[] v : VERTS) {
            vc.addVertex(identity, v[0], v[1], v[2]).setColor((int) v[5], (int) v[6], (int) v[7], 255).setUv(v[3], v[4]);
        }
        VERTS.clear();
        source.endBatch(Types.CORE);
    }

    public PowerCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PowerCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        float target = be.fullness();
        if (be.shownFull < 0) {
            be.shownFull = target;
        }
        be.shownFull += (target - be.shownFull) * 0.08F;
        float full = Mth.clamp(be.shownFull, 0.0F, 1.0F);
        int chamber = be.chamber();
        // the box it's traced through: just inside its frame alone; in a chamber, the room inside it
        float half = chamber > 0 ? (chamber - 1) / 2.0F - 0.55F : 0.37F;
        int skin = be.skin();
        ShaderLightRenderer.add(pose, 0.5F, 0.5F, 0.5F, chamber > 0 ? chamber + 1.0F + 3.0F * full : 3.0F + 4.0F * full,
                LIGHT[Math.floorMod(skin, LIGHT.length)]);
        Matrix4f m = pose.last().pose();
        org.joml.Vector3f centre = m.transformPosition(0.5F, 0.5F, 0.5F, new org.joml.Vector3f());
        // (the eye, from the middle: the pose is the block's place from the camera)
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        boolean late = queued || LatePass.later(PowerCoreRenderer::drawAll);
        queued = late;
        VertexConsumer vc = late ? null : buffers.getBuffer(Types.CORE);
        org.joml.Vector3f at = new org.joml.Vector3f();
        int r = skin & 255, g = Math.round(full * 255), b = chamber > 0 ? 255 : 0;
        // its containment rings (in a chamber, in the shader): turning faster the more comes in
        long nowMs = System.currentTimeMillis();
        float dt = be.ringAt == 0 ? 0.0F : Math.min(0.25F, (nowMs - be.ringAt) / 1000.0F);
        be.ringAt = nowMs;
        float speed = 0.25F + 2.2F * Mth.clamp((float) Math.log10(1.0 + be.rateIn()) / 5.5F, 0.0F, 1.0F);
        be.ringAngle = (float) ((be.ringAngle + dt * speed) % (Math.PI * 2));
        float phase = be.ringAngle / (float) (Math.PI * 2) * 0.45F;

        // each face of the box turned away from the eye - its far side (from inside: all of them): the near side would be
        // cut off by the screen's near plane as you came close (and the ray's traced from the eye in any case) - its
        // corners numbered x + 2y + 4z
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - half, ex - half, -ey - half, ey - half, -ez - half, ez - half};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * half, y = (((c >> 1) & 1) * 2 - 1) * half, z = (((c >> 2) & 1) * 2 - 1) * half;
                if (vc != null) {
                    vc.addVertex(m, 0.5F + x, 0.5F + y, 0.5F + z).setColor(r, g, b, 255).setUv(c + phase, half);
                } else if (VERTS.size() < 65536) {
                    m.transformPosition(0.5F + x, 0.5F + y, 0.5F + z, at);
                    VERTS.add(new float[] {at.x, at.y, at.z, c + phase, half, r, g, b});
                }
            }
        }
        if (chamber > 0) {
            veins(be, pose, buffers, chamber); // (after the box: getting another buffer would end its one)
        }
    }

    /**
     * Its energy veins: light pulsing along the outside of its chamber's frame edges - in from the corners towards the
     * middle of each edge while it's charging, out from the middles to the corners while it's giving out, faster the
     * more flows; still and dim with nothing flowing.
     */
    private static void veins(PowerCoreBlockEntity be, PoseStack pose, MultiBufferSource buffers, int chamber) {
        long in = be.rateIn(), out = be.rateOut(), net = in - out;
        boolean flowing = in > 0 || out > 0;
        boolean charging = net >= 0;
        float t = (float) LavaLampRenderer.seconds();
        float speed = 0.5F + 2.5F * Mth.clamp((float) Math.log10(1.0 + Math.abs(net) + Math.max(in, out) * 0.2) / 5.0F, 0.0F, 1.0F);
        int rgb = LIGHT[Math.floorMod(be.skin(), LIGHT.length)];
        float cr = (rgb >> 16 & 255) / 255.0F, cg = (rgb >> 8 & 255) / 255.0F, cb = (rgb & 255) / 255.0F;
        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float e = (chamber - 1) / 2.0F, len = e + 0.5F;
        float[] signs = {-1.0F, 1.0F};
        for (int axis = 0; axis < 3; axis++) {
            int b1 = (axis + 1) % 3, b2 = (axis + 2) % 3;
            for (float s1 : signs) {
                for (float s2 : signs) {
                    // the edge along this axis at (s1 e, s2 e): a strip on each of its two outer faces
                    for (int face = 0; face < 2; face++) {
                        int nAxis = face == 0 ? b1 : b2, cAxis = face == 0 ? b2 : b1;
                        float nOff = (face == 0 ? s1 : s2) * (e + 0.503F), cOff = (face == 0 ? s2 : s1) * e;
                        // (dim, all along it)
                        strip(v, m, axis, nAxis, cAxis, nOff, cOff, -len, len, 0.05F, cr, cg, cb, flowing ? 0.25F : 0.14F);
                        if (!flowing) {
                            continue;
                        }
                        for (int k = 0; k < 3; k++) {
                            float u = (t * speed / len + k / 3.0F) % 1.0F;
                            float from = charging ? u : 1.0F - u; // (0 at the corners, 1 at the middle)
                            float fade = Mth.sin((float) Math.PI * u);
                            for (float half : signs) {
                                float c = half * (1.0F - from) * len;
                                strip(v, m, axis, nAxis, cAxis, nOff, cOff, c - 0.22F, c + 0.22F, 0.09F, cr, cg, cb, 0.9F * fade);
                                strip(v, m, axis, nAxis, cAxis, nOff, cOff, c - 0.4F, c + 0.4F, 0.2F, cr, cg, cb, 0.25F * fade);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A glowing strip on a face: along {@code axis} from a to b, {@code w} wide across {@code cAxis}, at nOff out along nAxis. */
    private static void strip(VertexConsumer v, Matrix4f m, int axis, int nAxis, int cAxis, float nOff, float cOff, float a, float b, float w,
                              float r, float g, float bl, float alpha) {
        float[][] p = new float[4][3];
        float[] along = {a, b, b, a}, across = {-w, -w, w, w};
        for (int i = 0; i < 4; i++) {
            p[i][axis] = along[i];
            p[i][cAxis] = cOff + across[i];
            p[i][nAxis] = nOff;
        }
        int[] order = {0, 1, 2, 3, 3, 2, 1, 0}; // (both ways round: seen from either side)
        for (int i : order) {
            v.addVertex(m, 0.5F + p[i][0], 0.5F + p[i][1], 0.5F + p[i][2]).setColor(r, g, bl, alpha);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PowerCoreBlockEntity be) {
        return be.chamber() > 0;
    }

    @Override
    public AABB getRenderBoundingBox(PowerCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(1, (be.chamber() - 1) / 2.0) + be.chamber() + 7.0); // (as far as its light reaches)
    }
}
