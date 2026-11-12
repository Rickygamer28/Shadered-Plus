package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wall block with a little swinging door in it that only cats fit through: solid to everyone else, an OIIA cat
 * companion walks right through (and finds its way through it), the flap swinging as it goes. Right-click it with a
 * block to build it of that block (so it matches the wall: client/CatFlapModel).
 */
public class CatFlapBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<CatFlapBlock> CODEC = simpleCodec(CatFlapBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public CatFlapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CatFlapBlockEntity(pos, state);
    }

    /** A block that can be what the flap's built of: a plain full cube. */
    static boolean material(BlockState state, BlockGetter level, BlockPos pos) {
        return !(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) && state.getRenderShape() == RenderShape.MODEL
                && Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem bi && !(bi.getBlock() instanceof CatFlapBlock)
                && level.getBlockEntity(pos) instanceof CatFlapBlockEntity flap) {
            BlockState look = bi.getBlock().defaultBlockState();
            if (!material(look, level, pos)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide && look != flap.material()) {
                flap.setMaterial(look);
                level.playSound(null, pos, look.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    static boolean cat(CollisionContext context) {
        return context instanceof EntityCollisionContext e && e.getEntity() instanceof OiiaCatEntity;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return cat(context) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof OiiaCatEntity) {
            if (!state.getValue(OPEN)) {
                level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_CLIENTS);
                level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.4F);
            }
            level.scheduleTick(pos, this, 12);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.5F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.cat_flap").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.cat_flap.material").withStyle(ChatFormatting.DARK_GRAY));
    }
}
