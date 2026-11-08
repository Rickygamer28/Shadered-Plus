package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.example.skyblocks.compat.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A special containment frame: swapped in for any frame of a chamber, it still counts as one, and does something more.
 * A port moves items between the singularity and the containers next to it (in or out: sneak-click; right-click with an
 * item: only that item); a display shows what it holds on its sides; a monitor shows one item (right-click with it) and
 * how many of it there are; a redstone frame gives off a signal by how full it is.
 */
public class SpecialFrameBlock extends ContainmentFrameBlock {
    public enum Kind { PORT, DISPLAY, MONITOR, REDSTONE }

    /** (A port: putting out of the singularity, not into it.) */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    private final Kind kind;

    public SpecialFrameBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(OUTPUT, false));
    }

    public Kind kind() {
        return kind;
    }


    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OUTPUT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpecialFrameBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (kind == Kind.PORT || kind == Kind.MONITOR) && !level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.SPECIAL_FRAME.get(), SpecialFrameBlockEntity::serverTick) : null;
    }

    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // (an empty hand: sneak-click, or open it)
        }
        if (kind == Kind.PORT) {
            if (!level.isClientSide) {
                boolean same = ItemStack.isSameItemSameTags(be.filter(), stack);
                be.setFilter(same ? ItemStack.EMPTY : stack);
                player.displayClientMessage(same ? Component.translatable("message.skyblocks.port.filter_off")
                        : Component.translatable("message.skyblocks.port.filter", stack.getHoverName()), true);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, same ? 0.8F : 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == Kind.MONITOR) {
            if (!level.isClientSide) {
                be.setFilter(stack);
                be.updateCount();
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be) {
            if (kind == Kind.PORT) {
                if (!level.isClientSide) {
                    boolean out = !state.getValue(OUTPUT);
                    level.setBlock(pos, state.setValue(OUTPUT, out), Block.UPDATE_ALL);
                    player.displayClientMessage(Component.translatable(out ? "message.skyblocks.port.out" : "message.skyblocks.port.in")
                            .withStyle(out ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
                    level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.8F, out ? 0.6F : 0.9F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (kind == Kind.MONITOR && !be.filter().isEmpty()) {
                if (!level.isClientSide) {
                    be.setFilter(ItemStack.EMPTY);
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useWithoutItem121(state, level, pos, player, hit);
    }

    // ---- the redstone frame ----

    @Override
    public boolean isSignalSource(BlockState state) {
        return kind == Kind.REDSTONE;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == Kind.REDSTONE && level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be ? be.signal() : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return kind == Kind.REDSTONE;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be ? be.signal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + kind.name().toLowerCase(java.util.Locale.ROOT) + "_frame").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.special_frame").withStyle(ChatFormatting.DARK_GRAY));
    }
}
