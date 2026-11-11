package com.example.skyblocks.client;

import com.example.skyblocks.BonsaiBlock;
import com.example.skyblocks.BonsaiBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bonsai's little tree, built of its own kind's logs and leaves (any mod's) and drawn as Minecraft draws blocks
 * (BonsaiNatural): its real tree in miniature (natural style), or one of the shaped styles (BonsaiShapes), a block to a
 * pixel - grown to its age; a fruit tree, grown, with fruit hanging under its pads.
 */
public class BonsaiRenderer implements BlockEntityRenderer<BonsaiBlockEntity> {
    /** How big the tree is at each age (0 a sprout .. 3, 4 full). */
    private static final float[] GROW = {0.3F, 0.5F, 0.74F, 1.0F, 1.0F};
    /** The top of the soil, in pixels. */
    private static final float SOIL = 3.5F;

    public BonsaiRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BonsaiBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof BonsaiBlock) || state.getValue(BonsaiBlock.KIND) == BonsaiBlock.Kind.NONE) {
            return;
        }
        BonsaiBlock.Kind kind = state.getValue(BonsaiBlock.KIND);
        int age = state.getValue(BonsaiBlock.AGE);
        float g = GROW[age];
        poseStack.pushPose();
        poseStack.translate(0.5F, SOIL / 16.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw(state.getValue(BonsaiBlock.FACING))));

        BonsaiBlock.Style style = state.getValue(BonsaiBlock.STYLE);
        if (style == BonsaiBlock.Style.NATURAL && be.natural != null) {
            BonsaiNatural tree = BonsaiNatural.of(be);
            if (!tree.empty()) {
                // its own tree, shrunk to fit the pot: by its spread from the trunk, or its height, whichever's more
                float spread = 2 * Math.max(Math.max(Math.abs(tree.minX - 0.5F), Math.abs(tree.maxX - 0.5F)),
                        Math.max(Math.abs(tree.minZ - 0.5F), Math.abs(tree.maxZ - 0.5F)));
                float s = Math.min(0.72F / Math.max(1.0F, spread), 0.8F / Math.max(1.0F, tree.height)) * g;
                poseStack.scale(s, s, s);
                poseStack.translate(-0.5F, 0, -0.5F);
                tree.render(poseStack.last(), buffers, light);
                poseStack.popPose();
                return;
            }
        }
        // a shaped style (natural with its tree not here yet - just planted, on its way from the server: upright meanwhile)
        int shapeIndex = style.ordinal() < BonsaiShapes.SHAPES.length ? style.ordinal() : 0;
        BonsaiShapes.Shape shape = BonsaiShapes.SHAPES[shapeIndex];
        BlockState log = be.log != null ? be.log : kind == BonsaiBlock.Kind.AZALEA ? Blocks.OAK_LOG.defaultBlockState() : BonsaiBlock.log(kind);
        BlockState leaves = be.leaves != null ? be.leaves : BonsaiBlock.leaves(kind, false);
        BlockState flowering = age < 4 ? null : be.leaves != null ? be.flowering : kind == BonsaiBlock.Kind.AZALEA ? BonsaiBlock.leaves(kind, true) : null;
        int key = java.util.Objects.hash(shapeIndex, log, leaves, flowering);
        BonsaiNatural built = BonsaiNatural.shaped(be, key, () -> BonsaiShapes.blocks(shape, log, leaves, flowering));
        float px = g / 16.0F;
        poseStack.scale(px, px, px); // (a block to a pixel; the shape's blocks centred on the trunk already)
        built.render(poseStack.last(), buffers, light);
        if (age == 4 && !be.fruit.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            int i = 0;
            for (float[] spot : shape.fruitSpots) {
                if (i >= 3) {
                    break;
                }
                poseStack.pushPose();
                poseStack.translate(spot[0] + (i == 1 ? -0.7F : 0.8F), spot[1], spot[2] + 0.6F);
                poseStack.mulPose(Axis.YP.rotationDegrees(40 + i * 70));
                poseStack.scale(2.6F, 2.6F, 2.6F);
                mc.getItemRenderer().renderStatic(be.fruit, ItemDisplayContext.FIXED, light, overlay, poseStack, buffers, be.getLevel(),
                        (int) be.getBlockPos().asLong() + i);
                poseStack.popPose();
                i++;
            }
        }
        poseStack.popPose();
    }

    /** The shapes' front (+z) turned to the block's facing. */
    private static float yaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> -90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(BonsaiBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5);
    }
}
