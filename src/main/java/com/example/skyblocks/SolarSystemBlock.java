package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
    private final MapCodec<SolarSystemBlock> codec;

    public SolarSystemBlock(Properties properties, boolean hologram) {
        super(properties);
        this.hologram = hologram;
        this.codec = simpleCodec(p -> new SolarSystemBlock(p, hologram));
    }

    public boolean hologram() {
        return hologram;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return hologram ? PLANETARIUM : ORRERY;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SolarSystemBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(hologram ? "tooltip.skyblocks.planetarium" : "tooltip.skyblocks.orrery").withStyle(ChatFormatting.GRAY));
    }
}
