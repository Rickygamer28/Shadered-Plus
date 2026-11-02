package com.example.skyblocks.client;

import com.example.skyblocks.GlowColourBlockEntity;
import com.example.skyblocks.MagmaBasinBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

/**
 * A magma basin's lava: one quad at the surface (out to the block's edge where another basin joins), drawn in
 * shaders/core/magma_basin. Its UV is the world position, so joined basins make one pool.
 */
public class MagmaBasinRenderer implements BlockEntityRenderer<GlowColourBlockEntity> {
    static ShaderInstance shader;

    @EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Shaders {
        private Shaders() {}

        @SubscribeEvent
        public static void registerMagmaBasinRenderer(RegisterShadersEvent event) {
            ShaderLoad.register(event, ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "magma_basin"), DefaultVertexFormat.POSITION_TEX_COLOR, s -> shader = s);
        }
    }

    abstract static class Types extends RenderType {
        static final RenderType LAVA = create("skyblocks_magma_basin", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTexturingState(new TexturingStateShard("skyblocks_magma_time", () -> {
                            if (shader != null) {
                                shader.safeGetUniform("MagmaTime").set(LavaLampRenderer.seconds());
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

    public MagmaBasinRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GlowColourBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (shader == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(MagmaBasinBlock.NORTH)) {
            return;
        }
        float x0 = state.getValue(MagmaBasinBlock.WEST) ? 0.0F : 2.0F / 16.0F, x1 = state.getValue(MagmaBasinBlock.EAST) ? 1.0F : 14.0F / 16.0F;
        float z0 = state.getValue(MagmaBasinBlock.NORTH) ? 0.0F : 2.0F / 16.0F, z1 = state.getValue(MagmaBasinBlock.SOUTH) ? 1.0F : 14.0F / 16.0F;
        float y = MagmaBasinBlock.SURFACE;
        BlockPos p = be.getBlockPos();
        float wx = Math.floorMod(p.getX(), 1024), wz = Math.floorMod(p.getZ(), 1024);
        int c = GlowColourBlockEntity.shown(be, LavaLampRenderer.seconds());
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        VertexConsumer vc = buffers.getBuffer(Types.LAVA);
        Matrix4f m = poseStack.last().pose();
        vc.addVertex(m, x0, y, z1).setUv(wx + x0, wz + z1).setColor(r, g, b, 255);
        vc.addVertex(m, x1, y, z1).setUv(wx + x1, wz + z1).setColor(r, g, b, 255);
        vc.addVertex(m, x1, y, z0).setUv(wx + x1, wz + z0).setColor(r, g, b, 255);
        vc.addVertex(m, x0, y, z0).setUv(wx + x0, wz + z0).setColor(r, g, b, 255);
    }

    @Override
    public boolean shouldRenderOffScreen(GlowColourBlockEntity be) {
        return false;
    }
}
