package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cat's food bowl. Fill it with fish (up to four): a cat companion eats from it by itself when it's hungry or hurt.
 * Right-click with a dye to recolour it (it keeps its colour when broken).
 */
public class CatBowlBlock extends Block {
    public static final MapCodec<CatBowlBlock> CODEC = simpleCodec(CatBowlBlock::new);
    public static final IntegerProperty FOOD = IntegerProperty.create("food", 0, 4);
    public static final EnumProperty<DyeColor> COLOR = CatBedBlock.COLOR;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 3, 12);

    public CatBowlBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FOOD, 0).setValue(COLOR, DyeColor.LIGHT_BLUE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FOOD, COLOR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, next, level, pos, nextPos);
    }

    /** Fish a cat eats. */
    public static boolean food(ItemStack stack) {
        return stack.is(ItemTags.CAT_FOOD) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON) || stack.is(Items.TROPICAL_FISH);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye && state.getValue(COLOR) != dye.getDyeColor()) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, dye.getDyeColor()), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (food(stack)) {
            if (state.getValue(FOOD) >= 4) {
                return ItemInteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(FOOD, state.getValue(FOOD) + 1), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FISH_SWIM, SoundSource.BLOCKS, 0.6F, 1.4F);
                stack.consume(1, player);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** A cat's eaten a serving: one less in the bowl. */
    static void eaten(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CatBowlBlock && state.getValue(FOOD) > 0) {
            level.setBlock(pos, state.setValue(FOOD, state.getValue(FOOD) - 1), Block.UPDATE_ALL);
        }
    }

    /** Crumbs while a cat eats. */
    static void crumbs(ServerLevel level, BlockPos pos) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COD)), pos.getX() + 0.5, pos.getY() + 0.2,
                pos.getZ() + 0.5, 3, 0.1, 0.05, 0.1, 0.05);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.cat_bowl").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.dye").withStyle(ChatFormatting.DARK_GRAY));
    }
}
