package com.example.skyblocks.client;

import com.example.skyblocks.Structures;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A structure drawn in a menu, block by block, turning slowly (the guide book's and JEI's structure pages). */
public final class StructurePreview {
    private StructurePreview() {}

    /**
     * Draws {@code s} centred on {@code cx, cy}, {@code across} pixels wide; turned {@code yaw} degrees, looked at from
     * {@code pitch} above.
     */
    public static void draw(GuiGraphics g, Structures.Structure s, float cx, float cy, float across, float yaw, float pitch) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        PoseStack pose = g.pose();
        float scale = across / (s.size() * 1.6F); // (its diagonal fits)
        pose.pushPose();
        pose.translate(cx, cy, 200.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-s.size() / 2.0F, -s.size() / 2.0F, -s.size() / 2.0F);
        Lighting.setupFor3DItems();
        for (Map.Entry<BlockPos, BlockState> e : s.blocks().entrySet()) {
            BlockPos p = e.getKey();
            pose.pushPose();
            pose.translate(p.getX(), p.getY(), p.getZ());
            try {
                blocks.renderSingleBlock(e.getValue(), pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            } catch (RuntimeException ignored) {
                // (a block that can't be drawn on its own: left out)
            }
            pose.popPose();
        }
        g.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }
}
