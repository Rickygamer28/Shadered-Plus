package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A flat stepping stone: put on a koi pond, it sits in the water (its bottom under the surface) and you can walk
 * across the pond on a row of them. Frogs come and sit on them (the pond's frogs: shaders/core/koi_pond and
 * client/VanillaPondRenderer).
 */
public class SteppingStoneBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0);

    public SteppingStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Is there a stepping stone on this pond block? */
    public static boolean on(BlockGetter level, BlockPos pond) {
        return level.getBlockState(pond.above()).getBlock() instanceof SteppingStoneBlock;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.pond_stepping_stone").withStyle(ChatFormatting.GRAY));
    }
}
