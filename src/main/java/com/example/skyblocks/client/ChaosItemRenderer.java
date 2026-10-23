package com.example.skyblocks.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** The chaos block as an item (in hand, in the inventory, in item frames, dropped): a cube with the chaos shader. */
public class ChaosItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static RenderType type;

    public ChaosItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers,
                             int light, int overlay) {
        if (ChaosBlockRenderer.shader == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(type());
        Matrix4f pose = poseStack.last().pose();
        for (Direction d : Direction.values()) {
            ChaosBlockRenderer.face(consumer, pose, d, 0.0F, 0.0F, 0.0F);
        }
    }

    private static RenderType type() {
        if (type == null) {
            type = Types.chaos();
        }
        return type;
    }

    /** (A subclass only to reach RenderType's building blocks.) */
    private abstract static class Types extends RenderType {
        private Types() {
            super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
        }

        static RenderType chaos() {
            return create("skyblocks_chaos_item", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                    CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> ChaosBlockRenderer.shader))
                            .setTextureState(new RenderStateShard.TextureStateShard(ChaosBlockRenderer.CHAOS_TEXTURE, false, false))
                            .setTexturingState(new RenderStateShard.TexturingStateShard("skyblocks_chaos_uniforms", () -> {
                                if (ChaosBlockRenderer.shader != null) {
                                    // (the animation and the camera's turn, as for the blocks in the world)
                                    ChaosBlockRenderer.setUniforms(ChaosBlockRenderer.shader, Minecraft.getInstance().gameRenderer.getMainCamera(),
                                            Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
                                }
                            }, () -> {}))
                            .setCullState(NO_CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .createCompositeState(false));
        }
    }
}
