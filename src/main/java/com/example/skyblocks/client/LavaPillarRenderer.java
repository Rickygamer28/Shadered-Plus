package com.example.skyblocks.client;

import com.example.skyblocks.LavaPillarBlock;
import com.example.skyblocks.LavaPillarBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A lava lamp pillar's block: a box round its part of the glass, traced in shaders/core/lava_pillar with the whole
 * pillar's height and its bottom block's colours (each block drawn at once with its own place in the pillar).
 */
public class LavaPillarRenderer implements BlockEntityRenderer<LavaPillarBlockEntity> {
    static ShaderInstance shader;
    private static final Matrix4f POSE = new Matrix4f(), POSE_INV = new Matrix4f();
    private static float time, segY, height, seed;
    private static int wax, liquid;
    private static final float X0 = 0.14F, X1 = 0.86F, FOOT = 0.1875F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerLavaPillarShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "lava_pillar"), DefaultVertexFormat.POSITION, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader == null) {
            return;
        }
        shader.safeGetUniform("PoseMat").set(POSE);
        shader.safeGetUniform("PoseInv").set(POSE_INV);
        shader.safeGetUniform("Time").set(time);
        shader.safeGetUniform("SegY").set(segY);
        shader.safeGetUniform("Height").set(height);
        shader.safeGetUniform("Seed").set(seed);
        shader.safeGetUniform("Wax").set(((wax >> 16) & 255) / 255.0F, ((wax >> 8) & 255) / 255.0F, (wax & 255) / 255.0F);
        shader.safeGetUniform("Liquid").set(((liquid >> 16) & 255) / 255.0F, ((liquid >> 8) & 255) / 255.0F, (liquid & 255) / 255.0F);
    }

    abstract static class Types extends RenderType {
        static final RenderType GLASS = create("skyblocks_lava_pillar", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_lava_pillar_settings", LavaPillarRenderer::setUp, () -> {}))
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public LavaPillarRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LavaPillarBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (shader == null || level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        BlockPos bottom = LavaPillarBlock.bottom(level, pos);
        int h = LavaPillarBlock.height(level, bottom);
        LavaPillarBlockEntity base = level.getBlockEntity(bottom) instanceof LavaPillarBlockEntity b ? b : be;
        time = ((level.getGameTime() % 240000L) + partialTick) / 20.0F;
        segY = pos.getY() - bottom.getY();
        height = h;
        seed = (float) ((bottom.asLong() * 0x9E3779B97F4A7C15L >>> 44) % 1000L) / 37.0F;
        wax = base.wax();
        liquid = base.liquid();
        POSE.set(poseStack.last().pose());
        POSE_INV.set(POSE).invert();
        float y0 = segY == 0 ? FOOT : 0.0F, y1 = segY == h - 1 ? 1.0F - FOOT : 1.0F;
        MultiBufferSource.BufferSource source = DrawNow.now(buffers);
        box(source.getBuffer(Types.GLASS), poseStack.last().pose(), y0 - 0.002F, y1 + 0.002F);
        source.endBatch(Types.GLASS);
    }

    private static void box(VertexConsumer vc, Matrix4f m, float y0, float y1) {
        float[][] faces = {
                {X0, y0, X0, X1, y0, X0, X1, y0, X1, X0, y0, X1}, {X0, y1, X0, X0, y1, X1, X1, y1, X1, X1, y1, X0},
                {X0, y0, X0, X0, y1, X0, X1, y1, X0, X1, y0, X0}, {X0, y0, X1, X1, y0, X1, X1, y1, X1, X0, y1, X1},
                {X0, y0, X0, X0, y0, X1, X0, y1, X1, X0, y1, X0}, {X1, y0, X0, X1, y1, X0, X1, y1, X1, X1, y0, X1}};
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) {
                vc.addVertex(m, f[i], f[i + 1], f[i + 2]);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
