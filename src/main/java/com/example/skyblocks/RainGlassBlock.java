package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Rain glass: rain running down a window. Works like the water caustics block: right-click it with a full block or a
 * Shadered sky block to see it behind the wet glass; sneak + right-click with an empty hand to take it out. Drawn by
 * client/WaterCausticsModel with the rain textures (tools/make_rain_glass.py).
 */
public class RainGlassBlock extends WaterCausticsBlock {

    public RainGlassBlock(Properties properties) {
        super(properties);
    }


    @Override
    protected boolean holds(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return !(state.getBlock() instanceof WaterCausticsBlock) && !state.hasBlockEntity()
                && state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
                && net.minecraft.world.level.block.Block.isShapeFullBlock(state.getShape(level, pos));
    }

    /**
     * See-through like glass: it doesn't hide the faces next to it (a glass behind it showed a hole down to the sky), and
     * the faces between two rain glass blocks are left out, like glass.
     */
    @Override
    protected boolean skipRendering(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.BlockState next, net.minecraft.core.Direction dir) {
        return next.is(this) || super.skipRendering(state, next, dir);
    }

    @Override
    protected float getShadeBrightness(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.rain_glass").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.water_caustics_clear").withStyle(ChatFormatting.GRAY));
    }
}
