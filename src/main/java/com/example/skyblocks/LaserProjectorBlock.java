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
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A party laser: beams of light thrown out in moving patterns (client/LaserProjectorRenderer), bright in a fog machine's
 * fog, a spot of light where each lands, moving with the music. Put it on a floor, wall or ceiling. Right-click: on /
 * off; sneak + right-click: the next pattern; a dye: its colour; glowstone dust: rainbow. A redstone signal turns it on.
 */
public class LaserProjectorBlock extends BaseEntityBlock {
    public static final MapCodec<LaserProjectorBlock> CODEC = simpleCodec(LaserProjectorBlock::new);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        SHAPES[Direction.UP.ordinal()] = Block.box(4, 0, 4, 12, 6, 12);
        SHAPES[Direction.DOWN.ordinal()] = Block.box(4, 10, 4, 12, 16, 12);
        SHAPES[Direction.NORTH.ordinal()] = Block.box(4, 4, 10, 12, 12, 16);
        SHAPES[Direction.SOUTH.ordinal()] = Block.box(4, 4, 0, 12, 12, 6);
        SHAPES[Direction.WEST.ordinal()] = Block.box(10, 4, 4, 16, 12, 12);
        SHAPES[Direction.EAST.ordinal()] = Block.box(0, 4, 4, 6, 12, 12);
    }

    public LaserProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).ordinal()];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean dye = stack.getItem() instanceof DyeItem, rainbow = stack.is(Items.GLOWSTONE_DUST);
        if (!dye && !rainbow) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaserProjectorBlockEntity be) {
            be.setColour(dye ? ((DyeItem) stack.getItem()).getDyeColor().getTextureDiffuseColor() & 0xFFFFFF : -1);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.3F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaserProjectorBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.nextPattern();
                player.displayClientMessage(Component.translatable("message.skyblocks.laser.pattern",
                        Component.translatable("message.skyblocks.laser.pattern." + LaserProjectorBlockEntity.PATTERNS[be.pattern()]))
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                be.setOn(!be.on());
            }
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.5F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaserProjectorBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserProjectorBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.laser_projector").withStyle(ChatFormatting.GRAY));
    }
}
