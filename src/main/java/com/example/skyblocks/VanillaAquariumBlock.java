package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * An aquarium in plain Minecraft style: touching blocks make one glass tank of the game's own water, sand on the
 * bottom; client/VanillaAquariumRenderer puts the game's own fish and plants in it (tropical fish, cod, salmon,
 * pufferfish, axolotls, tadpoles; the ocean one dolphins, sea turtles, squid and glow squid too, and coral).
 */
public class VanillaAquariumBlock extends AquariumBlock {
    /**
     * (As the aquariums: client/AquariumRenderer draws its water, light, sand and frame - the aquariums' own - and
     * client/VanillaAquariumRenderer the game's fish and plants in it.)
     * UP: more of the same tank's water above - right above, or past a tunnel of air through the tank - so no surface
     * here, the water goes right up. DOWN: no sand here (more tank below, or air: the roof of a tunnel).
     */
    public VanillaAquariumBlock(Properties properties, boolean ocean) {
        super(properties, ocean);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        return net.minecraft.world.InteractionResult.PASS; // (no choosing fish: the game's own)
    }

    /** Is there more of this tank's water above (right above, or over a tunnel of air)? */
    private boolean waterAbove(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        for (int i = 1; i <= 16; i++) {
            BlockState s = level.getBlockState(pos.above(i));
            if (s.is(this)) return true;
            if (!s.isAir()) return false;
        }
        return false;
    }

    /** No sand here: more tank below, or nothing solid (air: the roof of a tunnel through it). */
    private boolean noSand(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) || !below.isSolidRender(level, pos.below());
    }

    private BlockState shaped(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.setValue(UP, waterAbove(level, pos)).setValue(DOWN, noSand(level, pos));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return shaped(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis() == Direction.Axis.Y ? shaped(state, level, pos) : state;
    }

    /** The tank under a tunnel of air (not touching this block) also changes when this is put or taken away. */
    private void refreshBelow(Level level, BlockPos pos) {
        for (int i = 1; i <= 16; i++) {
            BlockPos q = pos.below(i);
            BlockState s = level.getBlockState(q);
            if (s.is(this)) {
                BlockState now = shaped(s, level, q);
                if (now != s) level.setBlock(q, now, Block.UPDATE_CLIENTS);
                return;
            }
            if (!s.isAir()) return;
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this)) refreshBelow(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        super.onRemove(state, level, pos, now, moving);
        if (!now.is(this)) refreshBelow(level, pos);
    }


    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId().replace("block.", "tooltip.")).withStyle(ChatFormatting.GRAY));
    }
}
