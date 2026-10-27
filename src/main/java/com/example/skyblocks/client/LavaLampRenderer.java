package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.LavaLampBlock;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A lava lamp's glass: a box round it, traced in shaders/core/lava_lamp (the round glass, the liquid, the wax blobs
 * rising and sinking). The vertex colour carries the wax colour (RGB mode worked out here) and, in its alpha, on / off.
 */
public class LavaLampRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SkyBlocksMod.MOD_ID, "lava_lamp"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> shader = s);
        }
    }

    /** Seconds of game time (for the shaders' movement), kept small. */
    static float seconds() {
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level == null ? 0L : mc.level.getGameTime();
        return (float) (((t % 72_000L) + mc.getFrameTime()) / 20.0);
    }

    abstract static class Types extends RenderType {
        static final RenderType LAMP = create("skyblocks_lava_lamp", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_lava_lamp_camera", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("LampTime").set(seconds());
                                shader.safeGetUniform("ItemMode").set(0.0F);
                            }
                        }, () -> {}))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        /** As an item: drawn at once (LampInv is this one lamp's), see LavaLampItemRenderer. */
        static final RenderType ITEM = create("skyblocks_lava_lamp_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_lava_lamp_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("LampTime").set(seconds());
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

    public LavaLampRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        boolean on = be.getBlockState().hasProperty(LavaLampBlock.LIT) && be.getBlockState().getValue(LavaLampBlock.LIT);
        int c = GlowColourBlockEntity.shown(be, seconds());
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255, a = on ? 255 : 40;
        VertexConsumer vc = buffers.getBuffer(Types.LAMP);
        if (be.getBlockState().getBlock() instanceof com.example.skyblocks.BigLavaLampBlock) {
            // the big lamp: the same lamp stretched (see the shader), its glass from 0.55 to 1.62 blocks up
            box(vc, poseStack.last().pose(), 0.5F - 0.175F * BIG_H, 0.305F * BIG_V + BIG_Y, 0.5F - 0.175F * BIG_H,
                    0.5F + 0.175F * BIG_H, 0.805F * BIG_V + BIG_Y, 0.5F + 0.175F * BIG_H, r, g, b, a, 1.0F, seed(be));
        } else {
            box(vc, poseStack.last().pose(), 0.325F, 0.305F, 0.325F, 0.675F, 0.805F, 0.675F, r, g, b, a, 0.0F, seed(be));
        }
    }

    /** Each lamp's own timing (from where it stands). */
    private static float seed(GlowColourBlockEntity be) {
        long h = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        return (float) ((h >>> 40) & 0xFFFF) / 65536.0F;
    }

    /** The big lamp: the small one's shape scaled across, up, and moved up (the same numbers as the shader's). */
    static final float BIG_H = 1.55F, BIG_V = 2.2F, BIG_Y = -0.143F;
    /** The item being drawn: from its drawn position back into the lamp's own space. */
    static final Matrix4f ITEM_INV = new Matrix4f();

    /** A lamp's glass as an item, drawn at once with its own matrix (small or big, wax colour, on). */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, boolean big, int colour) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        int r = (colour >> 16) & 255, g = (colour >> 8) & 255, b = colour & 255;
        VertexConsumer vc = buffers.getBuffer(Types.ITEM);
        if (big) {
            box(vc, m, 0.5F - 0.175F * BIG_H, 0.305F * BIG_V + BIG_Y, 0.5F - 0.175F * BIG_H,
                    0.5F + 0.175F * BIG_H, 0.805F * BIG_V + BIG_Y, 0.5F + 0.175F * BIG_H, r, g, b, 255, 1.0F, 0.0F);
        } else {
            box(vc, m, 0.325F, 0.305F, 0.325F, 0.675F, 0.805F, 0.675F, r, g, b, 255, 0.0F, 0.0F);
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM); // (now, while the matrix is this lamp's)
        }
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox(GlowColourBlockEntity be) {
        net.minecraft.core.BlockPos p = be.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2, p.getZ() + 1);
    }

    /** A box's six faces, facing out. */
    static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int r, int g, int b, int a, float big, float seed) {
        float[][] f = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, // south
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, // north
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, // east
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, // west
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, // up
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, // down
        };
        for (float[] q : f) {
            for (int v = 0; v < 4; v++) {
                // uv: (which corner of the box + 10 if big, the lamp's seed) - the shader works out where it is from these
                int corner = (q[v * 3] == x1 ? 1 : 0) + (q[v * 3 + 1] == y1 ? 2 : 0) + (q[v * 3 + 2] == z1 ? 4 : 0);
                vc.vertex(m, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).uv(corner + 10.0F * big, seed).color(r, g, b, a).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }
}
