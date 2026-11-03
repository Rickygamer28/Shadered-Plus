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
 * A containment frame: built along the edges of a cube (5, 7 or 9 across) round a singularity, it makes a containment
 * chamber - the black hole grows to fill it (SingularityBlockEntity). Formed, its energy channels glow; it then works
 * as the singularity's own sides: right-click opens what it holds, hoppers and pipes on it put in and take out.
 */
public class ContainmentFrameBlock extends BaseEntityBlock {
    public static final MapCodec<ContainmentFrameBlock> CODEC = simpleCodec(ContainmentFrameBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public ContainmentFrameBlock(Properties properties) {
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FORMED) || !(level.getBlockEntity(pos) instanceof ContainmentFrameBlockEntity frame) || frame.master() == null) {
            return InteractionResult.PASS;
        }
        BlockPos core = frame.master();
        BlockState coreState = level.getBlockState(core);
        if (coreState.getBlock() instanceof SingularityBlock singularity) {
            return singularity.open(coreState, level, core, player);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ContainmentFrameBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.containment_frame").withStyle(ChatFormatting.GRAY));
    }
}
