package com.example.skyblocks.client;

import com.example.skyblocks.BlackHoleBlock;
import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.example.skyblocks.client.iris.IrisCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The black holes: a box round each hole's reach, traced in shaders/core/black_hole. Right before they are drawn, a
 * copy is made of what has been drawn so far (whatever picture is being drawn: the world, a mirror's), which the
 * shader looks up bent round the hole.
 */
public class BlackHoleRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;
    private static TextureTarget copy;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerBlackHoleRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "black_hole"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    /** Copies the picture being drawn now (its colour) into our texture, and hands it to the shader. */
    private static void copyScreen() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int w = Math.max(vp[2], 1), h = Math.max(vp[3], 1);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GL30.glBlitFramebuffer(vp[0], vp[1], vp[0] + w, vp[1] + h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
    }

    abstract static class Types extends RenderType {
        static final RenderType HOLE = hole("skyblocks_black_hole", false);
        /** With the eye inside the hole's box: not hidden by what's in the box (the floor under it, say) - all of it bent. */
        static final RenderType HOLE_INSIDE = hole("skyblocks_black_hole_inside", true);

        private static RenderType hole(String name, boolean inside) {
            return create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                    false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(() -> shader))
                            .setTexturingState(new TexturingStateShard(name + "_copy", () -> {
                                copyScreen();
                                if (shader != null) {
                                    shader.safeGetUniform("HoleTime").set(LavaLampRenderer.seconds());
                                    shader.safeGetUniform("ItemMode").set(0.0F);
                                }
                            }, () -> {}))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setWriteMaskState(inside ? COLOR_WRITE : COLOR_DEPTH_WRITE) // (its depth: see the shader)
                            .setDepthTestState(inside ? NO_DEPTH_TEST : LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL) // (both sides: still there with the eye inside the box)
                            .createCompositeState(false));
        }

        /** As an item: no world behind it to bend; drawn at once with its own matrix. */
        static final RenderType ITEM = create("skyblocks_black_hole_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_black_hole_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("HoleTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    static final Matrix4f ITEM_INV = new Matrix4f();

    public BlackHoleRenderer(BlockEntityRendererProvider.Context context) {}

    private static boolean big(GlowColourBlockEntity be) {
        return be.getBlockState().getBlock() instanceof BlackHoleBlock b && b.big();
    }

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        long h = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        float seed = (float) ((h >>> 40) & 0xFFFF) / 65536.0F * 0.9F;
        // the eye inside the box? (the main camera's; the box as in box())
        boolean big = big(be);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double reach = (big ? 1.5 : 0.36) * 1.03;
        boolean inside = Math.abs(cam.x - (be.getBlockPos().getX() + 0.5)) < reach && Math.abs(cam.z - (be.getBlockPos().getZ() + 0.5)) < reach
                && Math.abs(cam.y - (be.getBlockPos().getY() + (big ? 0.5 : 0.62))) < reach;
        int colour = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        if (IrisCompat.renderingShadowPass()) {
            return;
        }
        // drawn after the other block entities (shouldRenderOffScreen: the holes are among the world's "global" ones,
        // drawn last); what's still waiting to be drawn is drawn first - so it's in the picture the hole bends
        // and drawn now (left waiting, the last one would only be drawn at the very end of the frame, too late)
        RenderType type = inside ? Types.HOLE_INSIDE : Types.HOLE;
        boolean main = buffers == Minecraft.getInstance().renderBuffers().bufferSource();
        if (main) {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        }
        box(buffers.getBuffer(type), poseStack.last().pose(), big, colour, seed);
        if (main) {
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch(type);
        }
    }

    /** A hole as an item, drawn at once with this item's matrix (shrunk to fit for the big one). */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, boolean big) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        box(buffers.getBuffer(Types.ITEM), m, big, big ? 0xFFB070 : 0x9A6BFF, 0.3F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** The box round the hole's reach, its faces facing out (the same numbers as the shader's). */
    static void box(VertexConsumer vc, Matrix4f m, boolean big, int colour, float seed) {
        // (a little bigger than the reach: so its faces never lie flat on a floor, flickering against it)
        float cx = 0.5F, cy = big ? 0.5F : 0.62F, cz = 0.5F, reach = (big ? 1.5F : 0.36F) * 1.03F;
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                vc.addVertex(m, cx + ((k & 1) != 0 ? reach : -reach), cy + ((k & 2) != 0 ? reach : -reach), cz + ((k & 4) != 0 ? reach : -reach))
                        .setUv(k, (big ? 10.0F : 0.0F) + seed).setColor(r, g, b, 255);
            }
        }
    }

    public AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(big(be) ? 1.1 : 0.0);
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return true; // (so it is drawn after the rest: see render)
    }
}
