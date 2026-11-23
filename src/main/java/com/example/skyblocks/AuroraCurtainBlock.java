package com.example.skyblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Aurora Curtain: a slim rail that an aurora hangs from - folds of light down to the floor (or up to 8 blocks),
 * waving, brightest at its lower edge (client/AuroraCurtainRenderer, shaders/core/aurora_curtain). Curtains side by
 * side make one. Right-click it with a dye for its lower colour; sneak and right-click for its upper colour.
 */
public class AuroraCurtainBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<DyeColor> BOTTOM = EnumProperty.create("bottom", DyeColor.class);
    public static final EnumProperty<DyeColor> TOP = EnumProperty.create("top", DyeColor.class);
    private static final VoxelShape ALONG_X = box(0, 13, 6, 16, 16, 10), ALONG_Z = box(6, 13, 0, 10, 16, 16);

    public AuroraCurtainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(BOTTOM, DyeColor.LIME).setValue(TOP, DyeColor.PURPLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, BOTTOM, TOP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // (across the way you're looking: you see its face)
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean top = player.isShiftKeyDown();
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(top ? TOP : BOTTOM, dye.getDyeColor()), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, top ? 1.5F : 1.0F);
            player.displayClientMessage(Component.translatable(top ? "message.skyblocks.aurora_curtain.top" : "message.skyblocks.aurora_curtain.bottom",
                    Component.translatable("color.minecraft." + dye.getDyeColor().getName())), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleRenderedBlockEntity(ModBlockEntities.AURORA_CURTAIN.get(), pos, state);
    }
}
