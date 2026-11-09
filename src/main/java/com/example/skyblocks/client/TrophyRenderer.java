package com.example.skyblocks.client;

import com.example.skyblocks.TrophyBlock;
import com.example.skyblocks.TrophyBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** A trophy's plaque: who, which game, what score - engraved on the front of its stand. */
public class TrophyRenderer implements BlockEntityRenderer<TrophyBlockEntity> {
    private final Font font;

    public TrophyRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override
    public void render(TrophyBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (be.name().isEmpty() || !(be.getBlockState().getBlock() instanceof TrophyBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(TrophyBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        line(poseStack, buffers, be.name(), 2.6F / 16, 0.0035F, 0xFF3A2400, light);
        line(poseStack, buffers, be.game() + " - " + be.score(), 1.3F / 16, 0.0025F, 0xFF5A3A00, light);
        poseStack.popPose();
    }

    private void line(PoseStack poseStack, MultiBufferSource buffers, String text, float y, float scale, int colour, int light) {
        float room = 8.6F / 16;
        float w = font.width(text) * scale;
        if (w > room) scale *= room / w;
        poseStack.pushPose();
        poseStack.translate(0.5F, y, 3.95F / 16);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2.0F, -4.0F, colour, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        poseStack.popPose();
    }
}
