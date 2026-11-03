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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A portal mirror: a block whose front (FACING: the way it faces, towards you when placed) is a portal, linked to the
 * nearest other portal mirror of its colour, however far (PortalMirrors): look into it and you see what's in front of
 * the other one, walk into it and you come out of the other one. Set into a wall, it's a doorway to wherever the other
 * is. Side by side, facing the same way, they make one bigger portal. A dye: the colour of the whole portal. Only its
 * back half is solid (its front half walked into).
 */
public class PortalMirrorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<PortalMirrorBlock> CODEC = simpleCodec(PortalMirrorBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    private static final VoxelShape BACK_NORTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape BACK_SOUTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape BACK_WEST = Block.box(8, 0, 0, 16, 16, 16);
    private static final VoxelShape BACK_EAST = Block.box(0, 0, 0, 8, 16, 16);

    public PortalMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(COLOR, DyeColor.PURPLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) { // (the front half walked into: through the portal)
            case NORTH -> BACK_NORTH;
            case SOUTH -> BACK_SOUTH;
            case WEST -> BACK_WEST;
            default -> BACK_EAST;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof net.minecraft.server.level.ServerLevel server && old != state) {
            PortalMirrors.Registry.of(server).refresh(server, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        super.onRemove(state, level, pos, now, moving);
        if (level instanceof net.minecraft.server.level.ServerLevel server && !now.is(this)) {
            PortalMirrors.Registry.of(server).refresh(server, pos);
        }
    }

    /** Facing you; put against another portal mirror (beside, above or below it): its way and colour, one bigger portal. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        if (against.getBlock() instanceof PortalMirrorBlock && against.getValue(FACING).getAxis() != context.getClickedFace().getAxis()) {
            return defaultBlockState().setValue(FACING, against.getValue(FACING)).setValue(COLOR, against.getValue(COLOR));
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(COLOR) == dye.getDyeColor()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            PortalMirrors.Portal portal = PortalMirrors.component(level, pos);
            if (portal != null) {
                for (BlockPos p : portal.blocks()) {
                    level.setBlock(p, level.getBlockState(p).setValue(COLOR, dye.getDyeColor()), Block.UPDATE_ALL);
                }
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6F, 1.4F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        PortalMirrors.entityInside(state, level, pos, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.portal_mirror").withStyle(ChatFormatting.GRAY));
    }
}
