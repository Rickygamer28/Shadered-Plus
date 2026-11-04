package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
 * item: only that item); a display shows what it holds on its sides; an upgrade frame holds upgrades for it (right-click
 * with one; sneak-click takes one out); a redstone frame gives off a signal by how full it is.
 */
public class SpecialFrameBlock extends ContainmentFrameBlock {
    public enum Kind { PORT, DISPLAY, UPGRADE, REDSTONE }

    /** (A port: putting out of the singularity, not into it.) */
    public static final BooleanProperty OUTPUT = BooleanProperty.create("output");

    private final Kind kind;
    private final MapCodec<SpecialFrameBlock> codec = simpleCodec(p -> new SpecialFrameBlock(p, kind()));

    public SpecialFrameBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(OUTPUT, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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
        return kind == Kind.PORT && !level.isClientSide
                ? createTickerHelper(type, ModBlockEntities.SPECIAL_FRAME.get(), SpecialFrameBlockEntity::serverTick) : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (kind == Kind.PORT) {
            if (!level.isClientSide) {
                boolean same = ItemStack.isSameItemSameComponents(be.filter(), stack);
                be.setFilter(same ? ItemStack.EMPTY : stack);
                player.displayClientMessage(same ? Component.translatable("message.skyblocks.port.filter_off")
                        : Component.translatable("message.skyblocks.port.filter", stack.getHoverName()), true);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, same ? 0.8F : 1.2F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == Kind.UPGRADE && stack.getItem() instanceof UpgradeItem) {
            if (!level.isClientSide) {
                if (be.addUpgrade(stack)) {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 0.8F, 1.3F);
                } else {
                    player.displayClientMessage(Component.translatable("message.skyblocks.upgrade.full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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
            if (kind == Kind.UPGRADE && !be.upgrade().isEmpty()) {
                if (!level.isClientSide) {
                    ItemStack out = be.takeUpgrade();
                    if (!player.getInventory().add(out)) {
                        player.drop(out, false);
                    }
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be && !be.upgrade().isEmpty()) {
            Block.popResource(level, pos, be.upgrade().copy()); // (its upgrades aren't lost)
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ---- the redstone frame ----

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == Kind.REDSTONE;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == Kind.REDSTONE && level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be ? be.signal() : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return kind == Kind.REDSTONE;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpecialFrameBlockEntity be ? be.signal() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks." + kind.name().toLowerCase(java.util.Locale.ROOT) + "_frame").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.special_frame").withStyle(ChatFormatting.DARK_GRAY));
    }
}
