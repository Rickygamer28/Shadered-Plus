package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.example.skyblocks.compat.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Water caustics: the moving light on the bottom of a pool. Right-click it with a full block or a Shadered sky block to
 * put it under the water (used up, given back when taken out or broken); sneak + right-click with an empty hand to take
 * it out. Drawn by client/WaterCausticsModel (and client/WaterCausticsRenderer for a sky).
 */
public class WaterCausticsBlock extends BaseEntityBlock {

    public WaterCausticsBlock(Properties properties) {
        super(properties);
    }


    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterCausticsBlockEntity(pos, state);
    }

    /** Whether the block can go under the water: a plain full, solid cube drawn by its model, not this block. */
    public static boolean canHold(BlockState state, Level level, BlockPos pos) {
        return !(state.getBlock() instanceof WaterCausticsBlock) && !state.hasBlockEntity()
                && state.getRenderShape() == RenderShape.MODEL && state.isSolidRender(level, pos);
    }

    /** Whether this block can go in (the rain glass also takes see-through full blocks: glass, ice...). */
    protected boolean holds(BlockState state, Level level, BlockPos pos) {
        return canHold(state, level, pos);
    }

    protected ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof WaterCausticsBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean sky = ShaderedSkyItems.sky(stack) != null; // a Shadered sky block: that sky under the water
        if (!sky && !(stack.getItem() instanceof BlockItem item && holds(item.getBlock().defaultBlockState(), level, pos))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (ItemStack.isSameItemSameComponents(be.held(), stack)) {
            return ItemInteractionResult.SUCCESS; // (already that)
        }
        if (!level.isClientSide) {
            BlockState placed = stack.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : state;
            giveBack(be, player);
            be.setHeld(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            var sound = placed.getSoundType(level, pos, player);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    protected InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof WaterCausticsBlockEntity be) || be.held().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            giveBack(be, player);
            be.setHeld(ItemStack.EMPTY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** What was under the water goes back to the player (not in creative). */
    private static void giveBack(WaterCausticsBlockEntity be, Player player) {
        if (be.held().isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        ItemStack back = be.held().copy();
        if (!player.getInventory().add(back)) {
            player.drop(back, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WaterCausticsBlockEntity be && !be.held().isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.held().copy());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.water_caustics").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.water_caustics_clear").withStyle(ChatFormatting.GRAY));
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
