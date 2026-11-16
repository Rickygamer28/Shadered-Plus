package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The power core: energy storage you can watch fill (PowerCoreBlockEntity). Right-click: how much it holds, and its next
 * look (a star, a storm, a plasma globe, an atom); sneak + right-click: push mode on / off. A comparator reads how full
 * it is. Broken, its energy goes with the item.
 */
public class PowerCoreBlock extends BaseEntityBlock {
    public static final MapCodec<PowerCoreBlock> CODEC = simpleCodec(PowerCoreBlock::new);
    /** In its core chamber: no frame of its own (the chamber its frame). */
    public static final BooleanProperty IN_CHAMBER = BooleanProperty.create("in_chamber");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 2, 16), Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 2, 0, 2, 14, 2), Block.box(14, 2, 0, 16, 14, 2), Block.box(0, 2, 14, 2, 14, 16), Block.box(14, 2, 14, 16, 14, 16),
            Block.box(4, 4, 4, 12, 12, 12));
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);

    public PowerCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(IN_CHAMBER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IN_CHAMBER);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(IN_CHAMBER) ? CORE : SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(level, pos, player);
    }

    /** Right-click (here or on its chamber's frames): its next look and how much it holds; sneaking, push mode on / off. */
    public static InteractionResult use(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof PowerCoreBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                be.togglePush();
                level.playSound(null, pos, be.push() ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 1.4F);
                player.displayClientMessage(Component.translatable(be.push() ? "message.skyblocks.power_core.push_on" : "message.skyblocks.power_core.push_off")
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                be.nextSkin();
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F + be.skin() * 0.2F);
                player.displayClientMessage(Component.translatable("message.skyblocks.power_core.status",
                        Component.translatable("power_core.skyblocks.skin." + PowerCoreBlockEntity.SKINS[be.skin()]),
                        PowerCoreBlockEntity.fe(be.energy()), PowerCoreBlockEntity.fe(be.capacity())).withStyle(ChatFormatting.YELLOW), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moving) {
        if (!now.is(this) && level.getBlockEntity(pos) instanceof PowerCoreBlockEntity be) {
            be.unform();
        }
        super.onRemove(state, level, pos, now, moving);
    }

    // ---- its energy goes with it ----

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof PowerCoreBlockEntity c && be.getLevel() != null) {
            return List.of(c.asItem(be.getLevel().registryAccess()));
        }
        return List.of(new ItemStack(this));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof PowerCoreBlockEntity be && !be.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, be.asItem(level.registryAccess()));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PowerCoreBlockEntity be ? be.signal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.POWER_CORE.get(), PowerCoreBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.power_core").withStyle(ChatFormatting.GRAY));
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            long energy = tag.getLong("energy");
            int skin = Math.floorMod(tag.getInt("skin"), PowerCoreBlockEntity.SKINS.length);
            tooltip.add(Component.translatable("tooltip.skyblocks.power_core.holds", PowerCoreBlockEntity.fe(energy),
                    Component.translatable("power_core.skyblocks.skin." + PowerCoreBlockEntity.SKINS[skin])).withStyle(ChatFormatting.YELLOW));
        }
    }
}
