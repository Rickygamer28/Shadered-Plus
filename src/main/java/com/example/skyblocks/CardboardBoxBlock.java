package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An open cardboard box. If it fits, it sits: a cat companion climbs in now and then, only its head showing. */
public class CardboardBoxBlock extends Block {
    public static final MapCodec<CardboardBoxBlock> CODEC = simpleCodec(CardboardBoxBlock::new);
    private static final VoxelShape OUTLINE = Block.box(2, 0, 2, 14, 7, 14);
    private static final VoxelShape COLLISION = Shapes.or(Block.box(2, 0, 2, 14, 1, 14), Block.box(2, 0, 2, 14, 7, 3), Block.box(2, 0, 13, 14, 7, 14),
            Block.box(2, 0, 3, 3, 7, 13), Block.box(13, 0, 3, 14, 7, 13));

    public CardboardBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }

    /** Where a cat sits inside the box at pos. */
    static Vec3 inside(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1 / 16.0, pos.getZ() + 0.5);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, next, level, pos, nextPos);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.cardboard_box").withStyle(ChatFormatting.GRAY));
    }
}
