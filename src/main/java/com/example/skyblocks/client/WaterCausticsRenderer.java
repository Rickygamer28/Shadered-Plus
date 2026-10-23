package com.example.skyblocks.client;

import com.example.skyblocks.WaterCausticsBlockEntity;
import com.example.skyblocks.island.ShaderedSkyItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.SkyblockRenderer;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;

/** A Shadered sky under a water caustics block: its cube is queued with Shadered, exactly like a sky block's. */
public class WaterCausticsRenderer implements BlockEntityRenderer<WaterCausticsBlockEntity> {
    public WaterCausticsRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(WaterCausticsBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        ShaderedSkyItems.Sky sky = be.sky();
        if (sky == null) {
            return;
        }
        SkyblockBatchData data = SkyblockRenderer.getData(sky.type());
        if (data != null) {
            data.add(sky.pass(), be.getBlockPos(), new Matrix4f(poseStack.last().pose()), false, 1.0F);
        }
    }
}
