package com.example.skyblocks.client;

import com.example.skyblocks.GlitchBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A glitch block (GlitchBlockEntity): its cube (shaders/core/glitch_block), each face a grid of patches flicking between
 * the world behind it torn sideways, a wrong block texture, the missing-texture checks, static and black, its colours
 * split; and, its zone on, the world round it (5 blocks: shaders/core/glitch_zone) stuttering, tearing into shifted
 * slices and splitting in bursts. Both read the picture drawn so far (a copy, made as they're drawn): drawn after
 * everything else (LatePass, last). With a shader pack: the cube over the pack's image (its last finished one behind
 * it); no zone.
 */
public class GlitchRenderer implements BlockEntityRenderer<GlitchBlockEntity> {
    static ShaderInstance cubeShader, zoneShader;
    private static TextureTarget copy;
    /** The zone's reach (blocks from the block's middle). */
    public static final float REACH = 5.0F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "glitch_block"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> cubeShader = s);
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "glitch_zone"), DefaultVertexFormat.POSITION_TEX_COLOR,
                    s -> zoneShader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType CUBE = create("skyblocks_glitch_block", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> cubeShader))
                        .setTexturingState(new TexturingStateShard("skyblocks_glitch_copy", () -> {
                            copyScreen(false);
                            RenderSystem.setShaderTexture(2, TextureAtlas.LOCATION_BLOCKS);
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
        static final RenderType ZONE = create("skyblocks_glitch_zone", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1024, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> zoneShader))
                        .setTexturingState(new TexturingStateShard("skyblocks_glitch_zone_copy", () -> copyScreen(true), () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public GlitchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlitchBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (cubeShader == null || zoneShader == null) {
            return;
        }
        Matrix4f m = new Matrix4f(pose.last().pose());
        int seed = (int) (be.getBlockPos().asLong() * 2654435761L >>> 24) & 255;
        boolean pack = com.example.skyblocks.client.iris.IrisCompat.shaderPackInUse();
        boolean zone = be.zone() && !pack && !MirrorRenderer.isRenderingReflection();
        Runnable draw = () -> {
            ElevatorRenderer.drawNow(Types.CUBE, vc -> cube(vc, m, seed));
            if (zone) {
                ElevatorRenderer.drawNow(Types.ZONE, vc -> zoneBox(vc, m, seed));
            }
        };
        if (!LatePass.last(src -> draw.run())) {
            draw.run();
        }
    }

    /** Its cube's six faces (UV: across each, 0 .. 1; red: which face; green: its own seed). */
    private static void cube(VertexConsumer vc, Matrix4f m, int seed) {
        float[][][] faces = {
                {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
                {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}, {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
                {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int f = 0; f < 6; f++) {
            for (int c = 0; c < 4; c++) {
                float[] p = faces[f][c];
                vc.addVertex(m, p[0], p[1], p[2]).setColor(f, seed, 0, 255).setUv(uv[c][0], uv[c][1]);
            }
        }
    }

    /** The zone: a box round it, its faces turned away from the eye (from inside: all) - the shader keeps to its ball. */
    private static void zoneBox(VertexConsumer vc, Matrix4f m, int seed) {
        Vector3f centre = m.transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
        float ex = -centre.x, ey = -centre.y, ez = -centre.z, h = REACH;
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
        float[] towards = {-ex - h, ex - h, -ey - h, ey - h, -ez - h, ez - h};
        for (int f = 0; f < 6; f++) {
            if (towards[f] > 0) {
                continue;
            }
            for (int c : faces[f]) {
                float x = ((c & 1) * 2 - 1) * h, y = (((c >> 1) & 1) * 2 - 1) * h, z = (((c >> 2) & 1) * 2 - 1) * h;
                vc.addVertex(m, 0.5F + x, 0.5F + y, 0.5F + z).setColor(0, seed, 0, 255).setUv(c, h);
            }
        }
    }

    /** The picture so far (and its depth) for the shaders: Sampler0 (and Sampler1). With a shader pack: its last image. */
    private static void copyScreen(boolean depth) {
        if (com.example.skyblocks.client.iris.IrisDefer.drawing()) {
            com.example.skyblocks.client.iris.IrisIslandTarget.wantLastFinal = true;
            int last = com.example.skyblocks.client.iris.IrisIslandTarget.lastFinalTexture();
            if (last != 0) {
                RenderSystem.setShaderTexture(0, last);
                return;
            }
        }
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (copy == null) {
            copy = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        if (main.isStencilEnabled() && !copy.isStencilEnabled()) {
            copy.enableStencil();
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h,
                GL11.GL_COLOR_BUFFER_BIT | (depth ? GL11.GL_DEPTH_BUFFER_BIT : 0), GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderTexture(1, copy.getDepthTextureId());
    }

    @Override
    public boolean shouldRenderOffScreen(GlitchBlockEntity be) {
        return be.zone();
    }

    @Override
    public AABB getRenderBoundingBox(GlitchBlockEntity be) {
        return be.zone() ? new AABB(be.getBlockPos()).inflate(REACH) : new AABB(be.getBlockPos());
    }
}
