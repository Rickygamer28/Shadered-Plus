package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A spin pad (SpinPadBlockEntity): a low turntable an OIIA cat spins on, making Forge Energy. */
public class SpinPadBlock extends BaseEntityBlock {
    public static final MapCodec<SpinPadBlock> CODEC = simpleCodec(SpinPadBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public SpinPadBlock(Properties properties) {
        super(properties);
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
        return SHAPE;
    }

    /** A sky block: the aura's sky. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        ShaderedSkyItems.Sky sky = ShaderedSkyItems.sky(stack);
        if (sky == null || !(level.getBlockEntity(pos) instanceof SpinPadBlockEntity pad)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            pad.setSky(sky.type().name(), sky.pass().name());
            player.displayClientMessage(Component.translatable("message.skyblocks.spin_pad.sky", stack.getHoverName()), true);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** How it's going: its speed, the FE it's making, what it's holding. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpinPadBlockEntity pad) {
            String key = level.hasNeighborSignal(pos) ? "message.skyblocks.spin_pad.stopped"
                    : pad.occupied() ? "message.skyblocks.spin_pad.status" : "message.skyblocks.spin_pad.empty";
            player.displayClientMessage(Component.translatable(key, pad.rpm(), pad.lastFe(),
                    String.format("%,d", pad.energy.getEnergyStored())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpinPadBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SPIN_PAD.get(), level.isClientSide ? SpinPadBlockEntity::clientTick : SpinPadBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.spin_pad").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.spin_pad.energy", SpinPadBlockEntity.MAX_FE).withStyle(ChatFormatting.DARK_AQUA));
    }
}
