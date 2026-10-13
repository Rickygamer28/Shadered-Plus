package com.example.skyblocks.client.island;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A copied block entity (chest, bed, sign, banner, head...) inside a Sky Island, drawn by its normal renderer. */
final class IslandBlockEntity {
    private final BlockEntity blockEntity;
    private final int x, y, z;
    /** Shulker boxes: the real facing (the copy itself faces up). */
    @org.jetbrains.annotations.Nullable
    private final Direction turn;

    IslandBlockEntity(BlockEntity blockEntity, int x, int y, int z, @org.jetbrains.annotations.Nullable Direction turn) {
        this.blockEntity = blockEntity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.turn = turn;
    }

    /** Draws it on its own copy of the pose (a failing renderer can't unbalance the shared one); false if it failed. */
    boolean render(IslandView island, BlockEntityRenderDispatcher dispatcher, PoseStack poseStack, MultiBufferSource buffer,
                   float partialTick) {
        BlockEntityRenderer<BlockEntity> renderer = dispatcher.getRenderer(blockEntity);
        if (renderer == null) {
            return true; // drawn by its block model only (already in the island's mesh)
        }
        PoseStack own = new PoseStack();
        own.last().pose().set(poseStack.last().pose());
        own.last().normal().set(poseStack.last().normal());
        own.translate(x, y, z);
        if (turn != null && turn != Direction.UP) {
            own.translate(0.5F, 0.5F, 0.5F);
            own.mulPose(turn.getRotation());
            own.translate(-0.5F, -0.5F, -0.5F);
        }
        try {
            renderer.render(blockEntity, partialTick, own, buffer, island.lightAt(x + 0.5, y + 0.5, z + 0.5),
                    OverlayTexture.NO_OVERLAY);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
