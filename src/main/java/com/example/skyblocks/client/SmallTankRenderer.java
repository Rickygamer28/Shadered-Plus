package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.SmallTankBlock;
import com.example.skyblocks.SmallTankBlockEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The small fish tanks: one box round each whole tank (drawn by its corner block - the bottom one, the bottom left of a
 * slim tank), traced in shaders/core/small_tank in the tank's own space.
 */
public class SmallTankRenderer implements BlockEntityRenderer<SmallTankBlockEntity> {
    static ShaderInstance shader;

    @Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerSmallTankRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "small_tank"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void premultipliedOn() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    private static void premultipliedOff() {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    abstract static class Types extends RenderType {
        static final RenderType TANK = create("skyblocks_small_tank", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, true, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_small_tank_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("TankTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(0.0F);
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_small_tank_premultiplied", SmallTankRenderer::premultipliedOn, SmallTankRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));
        static final RenderType ITEM = create("skyblocks_small_tank_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> ViewState.upload(shader)))
                        .setTexturingState(new TexturingStateShard("skyblocks_small_tank_item", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("TankTime").set(LavaLampRenderer.seconds());
                                shader.safeGetUniform("ItemMode").set(1.0F);
                                shader.safeGetUniform("LampInv").set(ITEM_INV);
                            }
                        }, () -> {}))
                        .setTransparencyState(new TransparencyStateShard("skyblocks_small_tank_premultiplied_item", SmallTankRenderer::premultipliedOn, SmallTankRenderer::premultipliedOff))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    static final Matrix4f ITEM_INV = new Matrix4f();

    public SmallTankRenderer(BlockEntityRendererProvider.Context context) {}

    private static int shape(BlockState state) {
        return state.getBlock() instanceof SmallTankBlock t ? t.shape() : SmallTankBlock.BOWL;
    }

    @Override
    public void render(SmallTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        BlockPos pos = be.getBlockPos();
        int[] g = SmallTankBlock.group(be.getLevel(), pos, state);
        if (g[0] != pos.getX() || g[1] != pos.getY() || g[2] != pos.getZ()) {
            return; // (drawn by its corner block, the whole tank at once)
        }
        int shape = shape(state);
        int face = shape == SmallTankBlock.SLIM ? state.getValue(SmallTankBlock.FACING).get2DDataValue() : 0;
        // how lit: the brighter of the block light and the daylight here
        float day = Math.max(0.0F, Math.min(1.0F, (((ClientLevel) be.getLevel()).getSkyDarken(partialTick) - 0.2F) / 0.8F)) * 0.85F + 0.15F;
        float lit = Math.max(LightTexture.block(light) / 15.0F, LightTexture.sky(light) / 15.0F * day);
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        float seed = (float) ((h >>> 40) & 0xFFFF) / 65536.0F;
        box(buffers.getBuffer(Types.TANK), poseStack.last().pose(), shape, face, g[3], g[4], lit, be.variant(), seed);
    }

    /** A tank as an item: one block of it, drawn at once with this item's matrix. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers, int shape) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert().mul(ViewState.current()); // (1.20.1: the pose holds the camera's turn, which the shader takes out first)
        box(buffers.getBuffer(Types.ITEM), m, shape, 0, 1, 1, 1.0F, 0, 0.37F);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(Types.ITEM);
        }
    }

    /** The box round the tank, in its own space turned to its wall (the same numbers as the shader's), faces out. */
    static void box(VertexConsumer vc, Matrix4f m, int shape, int face, int w, int h, float lit, int variant, float seed) {
        float[] lo = shape == SmallTankBlock.BOWL ? new float[] {0.1F, 0.0F, 0.1F} : shape == SmallTankBlock.TALL ? new float[] {0.07F, 0.125F, 0.07F} : new float[] {0, 0, 0};
        float[] hi = shape == SmallTankBlock.BOWL ? new float[] {0.9F, 0.72F, 0.9F} : shape == SmallTankBlock.TALL ? new float[] {w - 0.07F, h - 0.125F, w - 0.07F} : new float[] {w, h, 0.3125F};
        // (x along the wall, z out of it: the tank's axes and corner in the block's space)
        float[][] ex = {{1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}};
        float[][] ez = {{0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};
        float[][] org = {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
        float[] x = ex[face], z = ez[face], o = org[face];
        int r = Math.min(w, 255), gg = Math.min(h, 255), b = Math.round(Math.max(0.0F, Math.min(1.0F, lit)) * 255.0F), a = variant;
        int[][] faces = {{4, 5, 7, 6}, {1, 0, 2, 3}, {5, 1, 3, 7}, {0, 4, 6, 2}, {6, 7, 3, 2}, {0, 1, 5, 4}};
        for (int[] f : faces) {
            for (int k : f) {
                float lx = (k & 1) != 0 ? hi[0] : lo[0], ly = (k & 2) != 0 ? hi[1] : lo[1], lz = (k & 4) != 0 ? hi[2] : lo[2];
                vc.vertex(m, o[0] + x[0] * lx + z[0] * lz, o[1] + ly, o[2] + x[2] * lx + z[2] * lz)
                        .uv(k + 8 * shape + 32 * face, seed).color(r, gg, b, a).endVertex();
            }
        }
    }

    /** (1.20.1: the block entity's getRenderBoundingBox says - kept for reference.) */
    public AABB getRenderBoundingBox(SmallTankBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p).expandTowards(0, SmallTankBlock.MAX, 0).inflate(SmallTankBlock.MAX, 0, SmallTankBlock.MAX);
    }

    @Override
    public boolean shouldRenderOffScreen(SmallTankBlockEntity be) {
        return false;
    }
}
