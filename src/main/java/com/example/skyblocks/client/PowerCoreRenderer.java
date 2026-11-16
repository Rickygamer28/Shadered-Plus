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
 * core chamber in one) traced through by shaders/core/energy_core - easing to how full it is; the storm's rain; and
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
        boolean inside = Math.abs(ex) < half && Math.abs(ey) < half && Math.abs(ez) < half;
        boolean late = queued || LatePass.later(PowerCoreRenderer::drawAll);
        queued = late;
        VertexConsumer vc = late ? null : buffers.getBuffer(Types.CORE);
        org.joml.Vector3f at = new org.joml.Vector3f();
        int r = skin & 255, g = Math.round(full * 255), b = chamber > 0 ? 255 : 0;
        // each face of the box turned towards the eye (from inside: all of them), its corners numbered x + 2y + 4z
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - half, ex - half, -ey - half, ey - half, -ez - half, ez - half};
        for (int f = 0; f < 6; f++) {
            if (!inside && towards[f] <= 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * half, y = (((c >> 1) & 1) * 2 - 1) * half, z = (((c >> 2) & 1) * 2 - 1) * half;
                if (vc != null) {
                    vc.addVertex(m, 0.5F + x, 0.5F + y, 0.5F + z).setColor(r, g, b, 255).setUv(c, half);
                } else if (VERTS.size() < 65536) {
                    m.transformPosition(0.5F + x, 0.5F + y, 0.5F + z, at);
                    VERTS.add(new float[] {at.x, at.y, at.z, c, half, r, g, b});
                }
            }
        }
        // the storm's rain: drops falling out of its cloud
        if (skin == 1 && be.getLevel() != null && !Minecraft.getInstance().isPaused()) {
            net.minecraft.util.RandomSource random = be.getLevel().random;
            float rate = (chamber > 0 ? chamber * 0.6F : 0.35F) * (0.3F + full);
            while (rate > 0) {
                if (random.nextFloat() < rate) {
                    double px = be.getBlockPos().getX() + 0.5 + (random.nextFloat() * 2 - 1) * half * 0.75;
                    double pz = be.getBlockPos().getZ() + 0.5 + (random.nextFloat() * 2 - 1) * half * 0.75;
                    double py = be.getBlockPos().getY() + 0.5 + half * 0.2;
                    be.getLevel().addParticle(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, px, py, pz, 0, 0, 0);
                }
                rate -= 1.0F;
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PowerCoreBlockEntity be) {
        return be.chamber() > 0;
    }

    @Override
    public AABB getRenderBoundingBox(PowerCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(Math.max(1, (be.chamber() - 1) / 2.0));
    }
}
