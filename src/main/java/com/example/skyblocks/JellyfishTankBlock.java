package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The jellyfish tank: a tall water column (2 blocks) with glowing jellyfish drifting and pulsing in it, bubbles rising.
 * Same controls as the lava lamps: right-click its light on / off (the jellyfish still glow a little in the dark);
 * a dye: their colour; sneak + right-click: RGB. Drawn by client/JellyTankRenderer (shaders/core/jelly_tank).
 */
public class JellyfishTankBlock extends BigLavaLampBlock {
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public JellyfishTankBlock(Properties properties) {
        super(properties);
    }


    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(40) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS, 0.25F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? GlowColourBlockEntity.jellyTank(pos, state) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.jellyfish_tank").withStyle(ChatFormatting.GRAY));
    }
}
