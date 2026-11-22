package com.example.skyblocks.client;

import com.example.skyblocks.ModSkyShapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A Sky Shape item (ModSkyShapes): its block's shape, every side showing its sky (as the block does). */
public class SkyShapeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private VoxelShape shape;
    private Block shapeOf;

    public SkyShapeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer,
                             int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof ModSkyShapes.Shape s)) {
            return;
        }
        RenderType skyType = SkyLiquidRenderer.skyType(s.sky());
        if (skyType == null) {
            return;
        }
        if (shapeOf != item.getBlock()) {
            shapeOf = item.getBlock();
            shape = shapeFor(item.getBlock(), s.kind());
        }
        VoxelShape box = shape;
        PoseStack.Pose pose = poseStack.last();
        if (context != ItemDisplayContext.GUI && com.example.skyblocks.client.iris.IrisIslandTarget.deferSky()) {
            // in the world (or in hand) with a shader pack: drawn at the end of the frame (see SkyLiquidRenderer)
            PoseStack.Pose copy = pose.copy();
            com.example.skyblocks.client.iris.IrisIslandTarget.deferSky(skyType, c -> SkyLiquidRenderer.shapeFaces(box, 0, 0, 0, null, new Posed(c, copy)));
            return;
        }
        SkyLiquidRenderer.shapeFaces(box, 0, 0, 0, null, new Posed(buffer.getBuffer(skyType), pose));
    }

    /** The shape shown for an item: a wall or a pane running across, a button bigger (like the game's own items). */
    private static VoxelShape shapeFor(Block block, ModSkyShapes.Kind kind) {
        BlockState state = block.defaultBlockState();
        switch (kind) {
            case WALL -> state = state.setValue(WallBlock.EAST_WALL, WallSide.LOW).setValue(WallBlock.WEST_WALL, WallSide.LOW);
            case PANE -> state = state.setValue(CrossCollisionBlock.EAST, true).setValue(CrossCollisionBlock.WEST, true);
            case BUTTON -> {
                return Shapes.box(5 / 16.0, 6 / 16.0, 6 / 16.0, 11 / 16.0, 10 / 16.0, 10 / 16.0);
            }
            default -> { }
        }
        return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Puts vertices through a pose. */
    private static final class Posed implements VertexConsumer {
        private final VertexConsumer out;
        private final PoseStack.Pose pose;

        Posed(VertexConsumer out, PoseStack.Pose pose) {
            this.out = out;
            this.pose = pose;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            out.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            out.setNormal(pose, x, y, z);
            return this;
        }
    }
}
