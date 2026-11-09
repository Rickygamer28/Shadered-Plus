package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The effect block's item: put against an effect block, the new one joins its group (its effect, running across both). */
public class EffectBlockItem extends BlockItem {
    public EffectBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos against = context.replacingClickedOnBlock() ? null : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockPos placed = context.getClickedPos();
        InteractionResult result = super.place(context);
        if (result.consumesAction() && against != null && !level.isClientSide
                && level.getBlockEntity(placed) instanceof EffectBlockEntity be && level.getBlockEntity(against) instanceof EffectBlockEntity other) {
            be.join(other);
        }
        return result;
    }
}
