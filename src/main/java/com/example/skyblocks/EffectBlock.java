package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An effect block: a full block showing an animated shader effect (client/EffectBlockRenderer). Right-click: the menu
 * (the effect, its colours, speed, size, brightness, pixelated). Placed against another effect block (with
 * EffectBlockItem), it joins that one's group: the same effect, running across all of them as one picture, and the
 * menu on any of them changes them all.
 */
public class EffectBlock extends BaseEntityBlock {
    public static final MapCodec<EffectBlock> CODEC = simpleCodec(EffectBlock::new);
    /** (how many blocks one group can change at once) */
    public static final int MAX_GROUP = 4096;

    public EffectBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED; // (all drawn by its renderer)
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof EffectBlockItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // (placing another against it: it joins)
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openEffectBlock(pos); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The blocks of this one's group touching it, through each other (it included): joined to it, or showing the very
     * same effect (so a wall built of the same settings is one picture, however it was put together).
     */
    public static List<BlockPos> group(BlockGetter level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        if (!(level.getBlockEntity(start) instanceof EffectBlockEntity first)) {
            return out;
        }
        long id = first.group();
        int key = first.settings().key();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        seen.add(start.immutable());
        todo.add(start.immutable());
        while (!todo.isEmpty() && out.size() < MAX_GROUP) {
            BlockPos p = todo.poll();
            out.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && level.getBlockEntity(n) instanceof EffectBlockEntity e
                        && (id != 0L && e.group() == id || e.settings().key() == key)) {
                    seen.add(n);
                    todo.add(n);
                }
            }
        }
        return out;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EffectBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.effect_block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.effect_block.join").withStyle(ChatFormatting.GRAY));
    }
}
