package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An aquarium: touching aquarium blocks make one glass tank of water with fish swimming in it, drawn by
 * client/AquariumRenderer (up close; further away the plain glass-and-water texture).
 */
public class AquariumBlock extends TransparentBlock {
    private final boolean ocean;

    public AquariumBlock(Properties properties) {
        this(properties, false);
    }

    /** ocean: the ocean aquarium (big sea animals, for giant tanks). */
    public AquariumBlock(Properties properties, boolean ocean) {
        super(properties);
        this.ocean = ocean;
    }

    public boolean ocean() {
        return ocean;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) { // which fish to show (a setting on this computer: the fish are only drawn there)
            com.example.skyblocks.client.ClientAccess.openAquarium(ocean); // (client-only class, never loaded on a server)
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(ocean ? "tooltip.skyblocks.ocean_aquarium" : "tooltip.skyblocks.aquarium").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState next, Direction side) {
        return next.is(this) || super.skipRendering(state, next, side); // (one tank: no glass between its blocks)
    }
}
