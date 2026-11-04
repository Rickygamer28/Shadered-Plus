package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A tank frame: built along the edges of a cube (5, 7 or 9 across) round a whirlpool singularity, with glass on the
 * cube's sides, it makes a containment tank - the whirlpool holds far more, and the tank fills with it. Formed, it works
 * as the whirlpool's own sides: right-click opens it, buckets and pipes on it fill and empty it.
 */
public class TankFrameBlock extends BaseEntityBlock {
    public static final MapCodec<TankFrameBlock> CODEC = simpleCodec(TankFrameBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public TankFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    private static BlockPos core(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(FORMED) && level.getBlockEntity(pos) instanceof TankFrameBlockEntity frame && frame.master() != null
                && level.getBlockState(frame.master()).getBlock() instanceof WhirlpoolBlock) {
            return frame.master();
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos core = core(state, level, pos);
        if (core != null && WhirlpoolBlock.useBucket(level, core, player, hand)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos core = core(state, level, pos);
        return core == null ? InteractionResult.PASS : WhirlpoolBlock.open(level, core, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankFrameBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.tank_frame").withStyle(ChatFormatting.GRAY));
    }
}
