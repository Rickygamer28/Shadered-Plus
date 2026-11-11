package com.example.skyblocks.client;

import com.example.skyblocks.ModComponents;
import com.example.skyblocks.PhotoFrameBlock;
import com.example.skyblocks.PhotoFrameBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** The picture in a photo frame (the frame itself is its block model). */
public class PhotoFrameRenderer implements BlockEntityRenderer<PhotoFrameBlockEntity> {
    public PhotoFrameRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PhotoFrameBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        ModComponents.Photo photo = be.photo();
        BlockState state = be.getBlockState();
        if (photo == null || !(state.getBlock() instanceof PhotoFrameBlock)) {
            return;
        }
        ResourceLocation tex = PhotoCache.get(photo.id());
        if (tex == null) {
            return; // (still coming)
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.getValue(PhotoFrameBlock.FACING).toYRot()));
        poseStack.translate(-0.5F, 0, -0.5F);
        // (as built facing north: the picture's front towards -z)
        boolean standing = state.getValue(PhotoFrameBlock.STANDING);
        float x0 = (standing ? 4 : 2) / 16.0F, x1 = (standing ? 12 : 14) / 16.0F;
        float y0 = (standing ? 2 : 3.5F) / 16.0F, y1 = (standing ? 8 : 12.5F) / 16.0F;
        float z = (standing ? 7.48F : 14.98F) / 16.0F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(tex));
        v(vc, pose, x1, y1, z, 0, 0, light, overlay);
        v(vc, pose, x1, y0, z, 0, 1, light, overlay);
        v(vc, pose, x0, y0, z, 1, 1, light, overlay);
        v(vc, pose, x0, y1, z, 1, 0, light, overlay);
        poseStack.popPose();
    }

    private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int overlay) {
        vc.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, -1);
    }
}
