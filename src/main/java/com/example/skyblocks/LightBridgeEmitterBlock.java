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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A light bridge emitter: a slim deck, flush with its bridge. Two whose light meets (straight across, round benders,
 * up to 8 blocks higher or lower) make a bridge between them, 64 blocks of it at most. Right-click: what kind - a
 * walkway, an arch, a moving walkway, a wall of light, a tunnel that carries you through; sneak-right-click: which way it
 * carries. A dye: its colour (deck and bridge); glowstone dust: rainbow. A redstone signal on either turns it off.
 */
public class LightBridgeEmitterBlock extends BaseEntityBlock {
    public static final MapCodec<LightBridgeEmitterBlock> CODEC = simpleCodec(LightBridgeEmitterBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Its light's colour, for the deck's lens and strips: a dye (0-15), or 16 for rainbow. */
    public static final IntegerProperty COLOR = IntegerProperty.create("color", 0, 16);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public LightBridgeEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, 16));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean dye = stack.getItem() instanceof DyeItem, rainbow = stack.is(Items.GLOWSTONE_DUST);
        if (!dye && !rainbow) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LightBridgeEmitterBlockEntity be) {
            int idx = dye ? ((DyeItem) stack.getItem()).getDyeColor().getId() : 16;
            int colour = dye ? ((DyeItem) stack.getItem()).getDyeColor().getTextureDiffuseColor() & 0xFFFFFF : -1;
            for (LightBridgeEmitterBlockEntity e : both(be)) {
                e.setColour(colour);
                level.setBlock(e.getBlockPos(), e.getBlockState().setValue(COLOR, idx), Block.UPDATE_ALL);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Right-click: its settings - what kind of bridge, how it looks, its colour. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            com.example.skyblocks.client.ClientAccess.openLightBridge(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static List<LightBridgeEmitterBlockEntity> both(LightBridgeEmitterBlockEntity be) {
        LightBridgeEmitterBlockEntity partner = be.partner();
        return partner != null && partner != be ? List.of(be, partner) : List.of(be);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof LightBridgeEmitterBlockEntity be) {
            be.clear(level);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightBridgeEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LIGHT_BRIDGE_EMITTER.get(),
                level.isClientSide ? LightBridgeEmitterBlockEntity::clientTick : LightBridgeEmitterBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.light_bridge_emitter").withStyle(ChatFormatting.GRAY));
    }
}
