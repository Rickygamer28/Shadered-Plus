package com.example.skyblocks.client;

import com.example.skyblocks.ClawMachineBlock;
import com.example.skyblocks.ClawMachineBlockEntity;
import com.example.skyblocks.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inside the claw machine's glass: a heap of plushies on its floor, the rails, and the claw - where whoever's playing is
 * steering it (sent to everyone near), or parked over the chute, swaying a little, when nobody is.
 */
public class ClawMachineRenderer implements BlockEntityRenderer<ClawMachineBlockEntity> {
    /** The inside, in pixels of the lower block (its front facing north): across, front to back, floor and top. */
    private static final float X0 = 2.5F, X1 = 13.5F, Z0 = 3.0F, Z1 = 14.0F, FLOOR = 12.0F, TOP = 27.0F;
    private static final BlockState CHROME = Blocks.IRON_BLOCK.defaultBlockState();

    public ClawMachineRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ClawMachineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!(be.getLevel() instanceof ClientLevel level) || !(be.getBlockState().getBlock() instanceof ClawMachineBlock)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(ClawMachineBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        int inside = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, be.getBlockPos().above());

        // the prizes: a heap of plushies, the same each time for this machine
        RandomSource r = RandomSource.create(be.getBlockPos().asLong());
        var items = Minecraft.getInstance().getItemRenderer();
        for (int i = 0; i < 14; i++) {
            float x = Mth.lerp(r.nextFloat(), X0 + 1.5F, X1 - 1.5F), z = Mth.lerp(r.nextFloat(), Z0 + 1.5F, Z1 - 1.5F);
            if (x > X1 - 3.5F && z < Z0 + 3.0F) continue; // (not on the chute)
            float y = FLOOR + (i >= 9 ? 1.6F : 0.0F); // (some on top of others)
            poseStack.pushPose();
            poseStack.translate(x / 16.0F, y / 16.0F + 0.1F, z / 16.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(r.nextFloat() * 360.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees((r.nextFloat() - 0.5F) * 30.0F));
            poseStack.scale(0.32F, 0.32F, 0.32F);
            items.renderStatic(new ItemStack(ModItems.PLUSHIES.get(r.nextInt(ModItems.PLUSHIES.size())).get()), ItemDisplayContext.FIXED,
                    inside, overlay, poseStack, buffers, level, 0);
            poseStack.popPose();
        }

        // the claw: where it's being steered, or parked over the chute
        boolean playing = level.getGameTime() - be.liveAt < 30L;
        float t = (level.getGameTime() + partialTick) / 20.0F;
        float cx = playing ? be.liveX : 0.14F + Mth.sin(t * 0.7F) * 0.01F;
        float cz = playing ? be.liveZ : 0.14F;
        float depth = playing ? be.liveDepth : 0.0F;
        boolean open = !playing || be.liveOpen;
        // (across: seen from the front, its left is +x)
        float px = Mth.lerp(cx, X1 - 1.0F, X0 + 1.0F), pz = Mth.lerp(cz, Z0 + 1.0F, Z1 - 1.0F);
        float clawY = Mth.lerp(depth, TOP - 3.0F, FLOOR + 2.5F);
        // the rails (left to right) and the bar across them
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, X0, TOP, Z0, X1, TOP + 0.4F, Z0 + 0.4F);
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, X0, TOP, Z1 - 0.4F, X1, TOP + 0.4F, Z1);
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, px - 0.3F, TOP - 0.3F, Z0, px + 0.3F, TOP + 0.3F, Z1);
        // the carriage, the cable, the hub and three prongs
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, px - 1.0F, TOP - 1.2F, pz - 1.0F, px + 1.0F, TOP - 0.2F, pz + 1.0F);
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, px - 0.15F, clawY + 1.0F, pz - 0.15F, px + 0.15F, TOP - 1.2F, pz + 0.15F);
        BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay, px - 0.8F, clawY, pz - 0.8F, px + 0.8F, clawY + 1.0F, pz + 0.8F);
        float spread = open ? 1.4F : 0.6F;
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2.0 / 3.0;
            float ox = (float) Math.cos(a) * spread, oz = (float) Math.sin(a) * spread;
            BlockSkin.box(CHROME, level, be.getBlockPos(), poseStack, buffers, inside, overlay,
                    px + ox - 0.2F, clawY - 2.0F, pz + oz - 0.2F, px + ox + 0.2F, clawY + 0.2F, pz + oz + 0.2F);
        }
        if (playing && be.liveHolding >= 0 && be.liveHolding < ModItems.PLUSHIES.size()) {
            poseStack.pushPose();
            poseStack.translate(px / 16.0F, (clawY - 2.2F) / 16.0F, pz / 16.0F);
            poseStack.scale(0.32F, 0.32F, 0.32F);
            items.renderStatic(new ItemStack(ModItems.PLUSHIES.get(be.liveHolding).get()), ItemDisplayContext.FIXED,
                    inside, overlay, poseStack, buffers, level, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ClawMachineBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
