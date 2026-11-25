package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
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
 * A core frame: built along the edges of a cube (5, 7 or 9 across) round a power core, its sides open or glass, it
 * makes a core chamber - the core holds far more, and what it holds grows to fill the chamber. Formed, it works as the
 * core's own sides: right-click it as the core, cables on it put energy in and take it out.
 */
public class CoreFrameBlock extends BaseEntityBlock {
    public static final MapCodec<CoreFrameBlock> CODEC = simpleCodec(CoreFrameBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CoreFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(FramePart.PART, FramePart.NONE).setValue(FramePart.QUAD, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FramePart.PART, FramePart.QUAD);
    }

    /** Put down: a power core near it looks at its chamber's frames again. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(state.getBlock())) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -4, -4), pos.offset(4, 4, 4))) {
                if (level.getBlockEntity(p) instanceof PowerCoreBlockEntity w) {
                    w.relinkSoon();
                }
            }
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    private static BlockPos core(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(FORMED) && level.getBlockEntity(pos) instanceof CoreFrameBlockEntity frame && frame.master() != null
                && level.getBlockState(frame.master()).getBlock() instanceof PowerCoreBlock) {
            return frame.master();
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos core = core(state, level, pos);
        return core == null ? InteractionResult.PASS : PowerCoreBlock.use(level, core, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreFrameBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.core_frame").withStyle(ChatFormatting.GRAY));
    }
}
