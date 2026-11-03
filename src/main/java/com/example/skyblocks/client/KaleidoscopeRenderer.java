package com.example.skyblocks.client;

import com.example.skyblocks.KaleidoscopeBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * The kaleidoscope's windows: a quad on each side inside the brass frame (the block's model), looked into in
 * shaders/core/kaleidoscope - each drawn at once with how far its own glass has turned. Sides against a solid block
 * aren't drawn.
 */
public class KaleidoscopeRenderer implements BlockEntityRenderer<KaleidoscopeBlockEntity> {
    static ShaderInstance shader;
    private static float turn, itemMode;
    private static final Matrix4f ITEM_INV = new Matrix4f();
    private static final float W0 = 2.0F / 16.0F, W1 = 14.0F / 16.0F, OUT = 0.002F;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerKaleidoscopeShader(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "kaleidoscope"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    private static void setUp() {
        if (shader != null) {
            MusicReactor.tick();
            shader.safeGetUniform("Turn").set(turn);
            shader.safeGetUniform("Boost").set(Math.min(MusicReactor.bass() * 1.5F, 1.0F));
            shader.safeGetUniform("ItemMode").set(itemMode);
            shader.safeGetUniform("LampInv").set(ITEM_INV);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType WINDOWS = create("skyblocks_kaleidoscope", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_kaleidoscope_turn", KaleidoscopeRenderer::setUp, () -> {}))
                        .setCullState(NO_CULL) // (the shader keeps the side facing you: items' matrices can turn them inside out)
                        .createCompositeState(false));

        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }
    }

    public KaleidoscopeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(KaleidoscopeBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null || !(be.getLevel() instanceof ClientLevel level)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int faces = 0;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isSolidRender(level, n)) {
                faces |= 1 << d.ordinal();
            }
        }
        if (faces == 0) {
            return;
        }
        turn = (float) (be.turn(level.getGameTime(), partialTick) % 100000.0);
        itemMode = 0.0F;
        int seed = (int) (Math.floorMod(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()) * 0x9E3779B97F4A7C15L >>> 40, 64L));
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource s ? s : Minecraft.getInstance().renderBuffers().bufferSource();
        windows(source.getBuffer(Types.WINDOWS), poseStack.last().pose(), faces, be.symmetry(), be.palette(), seed);
        source.endBatch(Types.WINDOWS);
    }

    /** The kaleidoscope as an item: all six windows, with this item's matrix. */
    static void drawItem(PoseStack poseStack, MultiBufferSource buffers) {
        if (shader == null) {
            return;
        }
        Matrix4f m = poseStack.last().pose();
        ITEM_INV.set(m).invert();
        Minecraft mc = Minecraft.getInstance();
        turn = mc.level == null ? 0.0F : (mc.level.getGameTime() % 72000L) / 20.0F;
        itemMode = 1.0F;
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource s ? s : mc.renderBuffers().bufferSource();
        windows(source.getBuffer(Types.WINDOWS), m, 63, 3, 0, 7);
        source.endBatch(Types.WINDOWS);
        itemMode = 0.0F;
    }

    /** The windows (the same places as the shader's vertex stage works out). */
    private static void windows(VertexConsumer vc, Matrix4f m, int faces, int symmetry, int palette, int seed) {
        int settings = symmetry + 8 * palette + 64 * seed;
        for (int face = 0; face < 6; face++) {
            if ((faces & (1 << face)) == 0) {
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                float cx = corner == 1 || corner == 2 ? W1 : W0, cy = corner >= 2 ? W1 : W0;
                float x, y, z;
                switch (face) {
                    case 0 -> { x = cx; y = -OUT; z = cy; }
                    case 1 -> { x = cx; y = 1.0F + OUT; z = cy; }
                    case 2 -> { x = cx; y = cy; z = -OUT; }
                    case 3 -> { x = cx; y = cy; z = 1.0F + OUT; }
                    case 4 -> { x = -OUT; y = cy; z = cx; }
                    default -> { x = 1.0F + OUT; y = cy; z = cx; }
                }
                vc.addVertex(m, x, y, z).setUv(face * 4 + corner, settings).setColor(255, 255, 255, 255);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
