package com.example.skyblocks.client;

import com.example.skyblocks.SkyTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.noodlegamer76.shadered.client.renderer.block.SkyblockEntityRenderer;
import com.noodlegamer76.shadered.client.util.skyblock.SkyblockPass;
import com.noodlegamer76.shadered.entity.block.SkyblockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shadered's sky block renderer has a switch over its own sky types; this one takes its place (registered after
 * Shadered's, in ClientSetup), queues our sky types the same way and hands every other sky to Shadered's renderer.
 */
public class OurSkyblockRenderer implements BlockEntityRenderer<SkyblockEntity> {
    private final SkyblockEntityRenderer<SkyblockEntity> shadered;

    public OurSkyblockRenderer(BlockEntityRendererProvider.Context context) {
        this.shadered = new SkyblockEntityRenderer<>(context);
    }

    @Override
    public void render(SkyblockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int light, int overlay) {
        int index = SkyTypes.index(entity.getBlockType());
        if (index < 0) {
            shadered.render(entity, partialTick, poseStack, buffer, light, overlay);
            return;
        }
        SkyblockPass pass = entity.getPass() != null ? entity.getPass() : SkyblockPass.NORMAL;
        ShaderedSkies.data(index).add(pass, entity.getBlockPos(), new Matrix4f(poseStack.last().pose()), false, 1.0F);
    }

    @Override
    public boolean shouldRenderOffScreen(SkyblockEntity entity) {
        return shadered.shouldRenderOffScreen(entity);
    }

    @Override
    public int getViewDistance() {
        return shadered.getViewDistance();
    }

    @Override
    public boolean shouldRender(SkyblockEntity entity, Vec3 camera) {
        return shadered.shouldRender(entity, camera);
    }
}
