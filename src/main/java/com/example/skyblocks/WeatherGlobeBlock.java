package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A weather globe: a glass ball on a wooden base with a little world inside showing the weather and the time of day out
 * here (client/WeatherGlobeRenderer). Right-click: shake it (a flurry of snow); with a Sky Island: that island inside
 * instead of the little house; sneak + right-click with an empty hand: the island back.
 */
public class WeatherGlobeBlock extends BaseEntityBlock {
    public static final MapCodec<WeatherGlobeBlock> CODEC = simpleCodec(WeatherGlobeBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 4, 14), Block.box(2.5, 4, 2.5, 13.5, 15.5, 13.5));

    /** Its base is another block's (WeatherGlobeBlockEntity.base, drawn by its renderer; the model: just the gold ring). */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CUSTOM_BASE =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("custom_base");
    /** (the base it comes with: picking this block puts it back) */
    public static final Block DEFAULT_BASE = net.minecraft.world.level.block.Blocks.STRIPPED_DARK_OAK_LOG;

    public WeatherGlobeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CUSTOM_BASE, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CUSTOM_BASE);
    }

    /** Whether this item's block can be a globe's base: a block drawn by its model. */
    public static boolean baseBlock(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem b
                && !(stack.getItem() instanceof com.example.skyblocks.island.SkyIslandItem)
                && !(b.getBlock() instanceof WeatherGlobeBlock)
                && b.getBlock().defaultBlockState().getRenderShape() == RenderShape.MODEL;
    }

    /** Sneak + right-click with a block: the base made of it (its own block: back as it came). */
    public static void setBase(Level level, BlockPos pos, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof WeatherGlobeBlockEntity be) || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem b)) {
            return;
        }
        BlockState base = b.getBlock().defaultBlockState();
        boolean custom = !base.is(DEFAULT_BASE);
        be.setBase(custom ? base : null);
        level.setBlock(pos, level.getBlockState(pos).setValue(CUSTOM_BASE, custom), Block.UPDATE_ALL);
        level.playSound(null, pos, base.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // (the base; the glass and what's inside: its renderer)
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof com.example.skyblocks.island.SkyIslandItem) || stack.get(ModComponents.ISLAND.get()) == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WeatherGlobeBlockEntity be) {
            ItemStack old = be.swapIsland(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (!old.isEmpty() && !player.getInventory().add(old)) {
                player.drop(old, false);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WeatherGlobeBlockEntity be) {
            if (player.isShiftKeyDown() && !be.islandItem().isEmpty()) {
                ItemStack old = be.swapIsland(ItemStack.EMPTY);
                if (!player.getInventory().add(old)) {
                    player.drop(old, false);
                }
            } else {
                be.shake();
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.4F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WeatherGlobeBlockEntity be && !be.islandItem().isEmpty()) {
            Block.popResource(level, pos, be.islandItem().copy());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WeatherGlobeBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.weather_globe").withStyle(ChatFormatting.GRAY));
    }
}
