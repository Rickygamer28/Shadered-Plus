package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
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
 * The fire tornado: a whirling column of real 3D flame in a tall glass case (2 blocks). Same controls as the lava
 * lamps (both halves work): right-click on / off; with a dye: the fire's colour; sneak + right-click: RGB. Drawn by
 * client/WallFireRenderer (shaders/core/wall_fire); taller with music.
 */
public class FireTornadoBlock extends BigLavaLampBlock {
    public static final MapCodec<FireTornadoBlock> CODEC = simpleCodec(FireTornadoBlock::new);
    private static final VoxelShape LOWER = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    private static final VoxelShape UPPER = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public FireTornadoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(14) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    0.6F + random.nextFloat() * 0.3F, 0.7F + random.nextFloat() * 0.3F, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? GlowColourBlockEntity.fire(pos, state) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.fire_tornado").withStyle(ChatFormatting.GRAY));
    }
}
