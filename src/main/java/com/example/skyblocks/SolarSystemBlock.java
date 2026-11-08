package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.example.skyblocks.compat.ItemInteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A working model of the solar system (client/SolarSystemRenderer, shaders/core/solar_system): the brass orrery (the
 * planets on arms) or the hologram planetarium (the system floating in a glass dome of stars, 3 blocks across).
 * Right-click: how fast (paused, slow, fast, real time); sneak + right-click: real sizes / sizes to see them by. A
 * redstone signal stops it; a comparator reads where the Earth is in its year.
 */
public class SolarSystemBlock extends BaseEntityBlock {
    private static final VoxelShape ORRERY = Block.box(1, 0, 1, 15, 5, 15);
    private static final VoxelShape PLANETARIUM = Block.box(1, 0, 1, 15, 5, 15);
    private final boolean hologram;

    public SolarSystemBlock(Properties properties, boolean hologram) {
        super(properties);
        this.hologram = hologram;
    }

    public boolean hologram() {
        return hologram;
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hologram ? PLANETARIUM : ORRERY;
    }

    /**
     * The planetarium: right-click with glass (any glass block or pane, any mod's) to take its dome off or put it back;
     * with an amethyst shard to make it bigger (normal, bigger, a lot bigger).
     */
    protected com.example.skyblocks.compat.ItemInteractionResult useItemOn121(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hologram && isTool(stack)) {
            useTool(stack, level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Glass (any glass block or pane, any mod's) or an amethyst shard: something to use on a planetarium. */
    public static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(net.minecraft.world.item.Items.AMETHYST_SHARD)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("glass"));
    }

    /** Glass on a planetarium: its dome off or back on; an amethyst shard: the next size. */
    public static void useTool(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SolarSystemBlockEntity be)) {
            return;
        }
        if (stack.is(net.minecraft.world.item.Items.AMETHYST_SHARD)) {
            be.cycleSize();
            player.displayClientMessage(Component.translatable("message.skyblocks.solar.size." + be.size()).withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.3F - 0.25F * be.size());
        } else {
            be.toggleDome();
            player.displayClientMessage(Component.translatable(be.dome() ? "message.skyblocks.solar.dome_on" : "message.skyblocks.solar.dome_off")
                    .withStyle(ChatFormatting.GOLD), true);
            level.playSound(null, pos, be.dome() ? SoundEvents.GLASS_PLACE : SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
    }

    protected net.minecraft.world.InteractionResult useWithoutItem121(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return use(level, pos, player);
    }

    /** Right-click (on its base, or on the model itself - SolarSystemEvents): the next speed; sneaking, real sizes or not. */
    public static InteractionResult use(Level level, BlockPos pos, Player player) {
        boolean hologram = level.getBlockState(pos).getBlock() instanceof SolarSystemBlock b && b.hologram();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarSystemBlockEntity be) {
            if (player.isShiftKeyDown()) {
                be.toggleSizes();
                player.displayClientMessage(Component.translatable(be.realSizes() ? "message.skyblocks.solar.real_sizes" : "message.skyblocks.solar.big_sizes")
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                be.cycleSpeed();
                player.displayClientMessage(Component.translatable("message.skyblocks.solar.speed." + be.speed()).withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, pos, hologram ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F + 0.15F * be.speed());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarSystemBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SolarSystemBlockEntity be ? be.signal() : 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SOLAR_SYSTEM.get(), SolarSystemBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarSystemBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(hologram ? "tooltip.skyblocks.planetarium" : "tooltip.skyblocks.orrery").withStyle(ChatFormatting.GRAY));
    }

    /** 1.20.1: one use() - an item first (useItemOn121), then the empty-hand use (useWithoutItem121), as 1.21 does. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                                      net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
                                                      net.minecraft.world.phys.BlockHitResult hit) {
        com.example.skyblocks.compat.ItemInteractionResult r = useItemOn121(player.getItemInHand(hand), state, level, pos, player, hand, hit);
        if (r != com.example.skyblocks.compat.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return r.result();
        }
        return hand == net.minecraft.world.InteractionHand.MAIN_HAND ? useWithoutItem121(state, level, pos, player, hit) : net.minecraft.world.InteractionResult.PASS;
    }
}
