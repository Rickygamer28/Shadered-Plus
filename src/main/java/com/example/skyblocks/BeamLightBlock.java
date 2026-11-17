package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A beam light: a small white lamp on a little stand - on a floor, a ceiling or a wall - throwing a thin, bright white
 * beam the way it's pointed (client/SpotlightRenderer draws it, as a spotlight's). Placed, it points the way you were
 * looking: stand behind it, look at what it should light (a prism, say) and place it. Sneak + right-click: aim it again
 * (it follows where you look until you right-click). Right-click: on / off. Its block entity is a spotlight's.
 */
public class BeamLightBlock extends SpotlightBlock {
    public static final MapCodec<BeamLightBlock> CODEC = simpleCodec(BeamLightBlock::new);
    private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
            Direction.UP, Block.box(5, 0, 5, 11, 7, 11), Direction.DOWN, Block.box(5, 9, 5, 11, 16, 11),
            Direction.NORTH, Block.box(5, 5, 9, 11, 11, 16), Direction.SOUTH, Block.box(5, 5, 0, 11, 11, 7),
            Direction.WEST, Block.box(9, 5, 5, 16, 11, 11), Direction.EAST, Block.box(0, 5, 5, 7, 11, 11));

    public BeamLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Placed: pointing the way the placer was looking. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof SpotlightBlockEntity be) {
            Vec3 look = placer.getLookAngle();
            be.aim((float) Math.atan2(look.x, look.z), (float) Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        }
    }

    /** (Always white: no dyes.) */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.beam_light").withStyle(ChatFormatting.GRAY));
    }
}
