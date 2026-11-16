package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.TimeCrystalBlockEntity;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import org.joml.Vector3f;

/**
 * A time crystal (TimeCrystalBlockEntity): the crystal and its rune rings, in 3D (a box traced through by
 * shaders/core/time_crystal, as the power core's); while it runs, a clock face on the ground round it
 * (shaders/core/clock_face) and the dome over what it hurries (shaders/core/time_dome: its sky inside, a glassy rim
 * outside) and the air inside it bent (shaders/core/time_warp, without a shader pack), fading in and out; and its violet
 * light (ShaderLightRenderer). Drawn after the coloured lights (LatePass), in this order: the dome's far side, the warp,
 * the crystal, the clock face, the dome's near side.
 */
public class TimeCrystalRenderer implements BlockEntityRenderer<TimeCrystalBlockEntity> {
    static ShaderInstance crystalShader, domeShader, faceShader, warpShader;
    private static com.mojang.blaze3d.pipeline.TextureTarget copy;
    /** The box round the crystal: its middle (above the block's base) and half-size. */
    private static final float CY = 1.25F, HALF = 1.45F;
    private static final int AROUND = 64, UP = 16;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_crystal"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> crystalShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_dome"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> domeShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "clock_face"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> faceShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "time_warp"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> warpShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CRYSTAL = create("skyblocks_time_crystal", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> crystalShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType DOME_IN = create("skyblocks_time_dome_in", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> domeShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType DOME_OUT = create("skyblocks_time_dome_out", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> domeShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
        static final RenderType FACE = create("skyblocks_clock_face", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> faceShader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        /** The warp: over a copy of the picture (and its depth), made as it's drawn; not depth tested (the shader works out what's inside). */
        static final RenderType WARP = create("skyblocks_time_warp", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> warpShader))
                        .setTexturingState(new TexturingStateShard("skyblocks_time_warp_copy", TimeCrystalRenderer::copyScreen, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public TimeCrystalRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TimeCrystalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (crystalShader == null || domeShader == null || faceShader == null) {
            return;
        }
        float on = Mth.clamp(be.shownOn, 0.0F, 1.0F);
        if (on > 0.05F) {
            ShaderLightRenderer.add(pose, 0.5F, CY, 0.5F, 4.0F + 3.0F * on, 0xB070FF);
        }
        Matrix4f m = new Matrix4f(pose.last().pose());
        float spin = be.spin + be.spinSpeed * partialTick;
        int speed = be.speedIndex();
        float radius = be.radius() + 0.5F;
        boolean dome = be.dome() && on > 0.01F;
        java.util.function.Consumer<MultiBufferSource> draw = src -> {
            if (dome) {
                dome(src.getBuffer(Types.DOME_IN), m, radius, speed, 0, on);
            }
            if (dome && warpShader != null && !com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse() && !MirrorRenderer.isRenderingReflection()) {
                warp(src.getBuffer(Types.WARP), m, radius, speed, on);
            }
            crystal(src.getBuffer(Types.CRYSTAL), m, spin, on, speed);
            if (on > 0.01F) {
                face(src.getBuffer(Types.FACE), m, radius, speed, on);
            }
            if (dome) {
                dome(src.getBuffer(Types.DOME_OUT), m, radius, speed, 255, on);
            }
        };
        if (!LatePass.later(src -> {
            draw.accept(src);
            endInOrder(src);
        })) {
            draw.accept(buffers);
            if (buffers instanceof MultiBufferSource.BufferSource src) {
                endInOrder(src);
            }
        }
    }

    private static void endInOrder(MultiBufferSource.BufferSource src) {
        src.endBatch(Types.DOME_IN);
        src.endBatch(Types.WARP);
        src.endBatch(Types.CRYSTAL);
        src.endBatch(Types.FACE);
        src.endBatch(Types.DOME_OUT);
    }

    /** The crystal's box: its faces turned away from the eye (from inside it: all), as the power core's. */
    private static void crystal(VertexConsumer vc, Matrix4f m, float spin, float on, int speed) {
        Vector3f centre = m.transformPosition(0.5F, CY, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z;
        int turn = (int) (Mth.frac(spin) * 65536.0F) & 0xFFFF;
        int r = turn >> 8, a = turn & 255, g = Math.round(on * 255), b = speed;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - HALF, ex - HALF, -ey - HALF, ey - HALF, -ez - HALF, ez - HALF};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * HALF, y = (((c >> 1) & 1) * 2 - 1) * HALF, z = (((c >> 2) & 1) * 2 - 1) * HALF;
                vc.addVertex(m, 0.5F + x, CY + y, 0.5F + z).setColor(r, g, b, a).setUv(c, HALF);
            }
        }
    }

    /** The clock face: a square on the ground (the shader cuts the circle), just above it. */
    private static void face(VertexConsumer vc, Matrix4f m, float radius, int speed, float on) {
        int g = Math.round(radius), a = Math.round(on * 255);
        float s = radius + 0.3F, y = 0.03F;
        float k = s / radius;
        vc.addVertex(m, 0.5F - s, y, 0.5F - s).setColor(speed, g, 0, a).setUv(-k, -k);
        vc.addVertex(m, 0.5F - s, y, 0.5F + s).setColor(speed, g, 0, a).setUv(-k, k);
        vc.addVertex(m, 0.5F + s, y, 0.5F + s).setColor(speed, g, 0, a).setUv(k, k);
        vc.addVertex(m, 0.5F + s, y, 0.5F - s).setColor(speed, g, 0, a).setUv(k, -k);
    }

    /** The dome: a half ball (side 0: its inside, the sky; 255: its outside, the rim). */
    private static void dome(VertexConsumer vc, Matrix4f m, float radius, int speed, int side, float on) {
        int a = Math.round(on * 255);
        for (int i = 0; i < AROUND; i++) {
            float az0 = i * Mth.TWO_PI / AROUND, az1 = (i + 1) * Mth.TWO_PI / AROUND;
            for (int j = 0; j < UP; j++) {
                float el0 = j * Mth.HALF_PI / UP, el1 = (j + 1) * Mth.HALF_PI / UP;
                point(vc, m, radius, az0, el0, speed, side, a);
                point(vc, m, radius, az1, el0, speed, side, a);
                point(vc, m, radius, az1, el1, speed, side, a);
                point(vc, m, radius, az0, el1, speed, side, a);
            }
        }
    }

    /** The warp's shape: the dome closed off (its half ball, its floor - a disc). */
    private static void warp(VertexConsumer vc, Matrix4f m, float radius, int speed, float on) {
        int g = Math.min(255, Math.round(radius * 10.0F)), a = Math.round(on * 255);
        float rr = g / 10.0F; // (as the shader reads it)
        for (int i = 0; i < AROUND; i++) {
            float az0 = i * Mth.TWO_PI / AROUND, az1 = (i + 1) * Mth.TWO_PI / AROUND;
            for (int j = 0; j < UP; j++) {
                float el0 = j * Mth.HALF_PI / UP, el1 = (j + 1) * Mth.HALF_PI / UP;
                warpPoint(vc, m, rr, az0, el0, speed, g, a);
                warpPoint(vc, m, rr, az1, el0, speed, g, a);
                warpPoint(vc, m, rr, az1, el1, speed, g, a);
                warpPoint(vc, m, rr, az0, el1, speed, g, a);
            }
            vc.addVertex(m, 0.5F, 0.0F, 0.5F).setColor(speed, g, 255, a).setUv(0.0F, 0.0F);
            vc.addVertex(m, 0.5F, 0.0F, 0.5F).setColor(speed, g, 255, a).setUv(0.0F, 0.0F);
            vc.addVertex(m, 0.5F + rr * Mth.cos(az1), 0.0F, 0.5F + rr * Mth.sin(az1)).setColor(speed, g, 255, a).setUv(Mth.cos(az1), Mth.sin(az1));
            vc.addVertex(m, 0.5F + rr * Mth.cos(az0), 0.0F, 0.5F + rr * Mth.sin(az0)).setColor(speed, g, 255, a).setUv(Mth.cos(az0), Mth.sin(az0));
        }
    }

    private static void warpPoint(VertexConsumer vc, Matrix4f m, float radius, float az, float el, int speed, int g, int a) {
        float c = Mth.cos(el);
        vc.addVertex(m, 0.5F + radius * c * Mth.cos(az), radius * Mth.sin(el), 0.5F + radius * c * Mth.sin(az))
                .setColor(speed, g, 0, a).setUv(c * Mth.cos(az), c * Mth.sin(az));
    }

    /** Copies the picture drawn so far and its depth, for the warp (Sampler0, Sampler1). */
    private static void copyScreen() {
        int[] vp = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        com.mojang.blaze3d.pipeline.RenderTarget main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (copy == null) {
            copy = new com.mojang.blaze3d.pipeline.TextureTarget(w, h, true, net.minecraft.client.Minecraft.ON_OSX);
            copy.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, net.minecraft.client.Minecraft.ON_OSX);
            copy.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        }
        if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
            copy.enableStencil(); // (the same depth format as the picture's, or its depth can't be copied)
        }
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, draw);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        org.lwjgl.opengl.GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h,
                org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, read);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, draw);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(1, copy.getDepthTextureId());
    }

    private static void point(VertexConsumer vc, Matrix4f m, float radius, float az, float el, int speed, int side, int a) {
        float c = Mth.cos(el);
        vc.addVertex(m, 0.5F + radius * c * Mth.cos(az), radius * Mth.sin(el), 0.5F + radius * c * Mth.sin(az))
                .setColor(speed, side, 0, a).setUv(az, el);
    }

    @Override
    public boolean shouldRenderOffScreen(TimeCrystalBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(TimeCrystalBlockEntity be) {
        double r = be.radius() + 1.0;
        return new AABB(be.getBlockPos()).inflate(r, 2.0, r).expandTowards(0, r, 0);
    }
}
