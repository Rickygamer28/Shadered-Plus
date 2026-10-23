package com.example.skyblocks.client;

import com.example.skyblocks.FogMachineBlock;
import com.example.skyblocks.FogMachineBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** The outline of the fog machine's area, when shown (while its menu is open, following the menu's values). */
public class FogMachineRenderer implements BlockEntityRenderer<FogMachineBlockEntity> {
    public FogMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FogMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        AABB area;
        if (Minecraft.getInstance().screen instanceof FogMachineScreen screen && screen.pos().equals(be.getBlockPos())) {
            if (!screen.showArea()) {
                return;
            }
            Direction facing = be.getBlockState().getValue(FogMachineBlock.FACING);
            area = FogMachineBlockEntity.area(be.getBlockPos(), facing, screen.value(0), screen.value(1), screen.value(2), screen.value(3), screen.value(4));
        } else {
            if (!be.showArea()) {
                return;
            }
            area = be.area();
        }
        area = area.move(-be.getBlockPos().getX(), -be.getBlockPos().getY(), -be.getBlockPos().getZ());
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), area, 0.75F, 0.85F, 1.0F, 1.0F);
    }

    @Override
    public boolean shouldRenderOffScreen(FogMachineBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(FogMachineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(FogMachineBlockEntity.MAX_REACH + 1, FogMachineBlockEntity.MAX_HEIGHT + 1, FogMachineBlockEntity.MAX_REACH + 1);
    }
}
