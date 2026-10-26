package com.example.skyblocks;

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
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The shader effect test blocks (our own takes on looks seen in other mods): the warp block, the rift and the static
 * block. A dye tints the effect; sneak + right-click with an empty hand: back to its own colours. Drawn by
 * client/FxRenderer.
 */
public abstract class FxBlock extends BaseEntityBlock {
    private final String tooltip;

    protected FxBlock(Properties properties, String tooltip) {
        super(properties);
        this.tooltip = tooltip;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof GlowColourBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor());
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof GlowColourBlockEntity be) {
            if (!level.isClientSide) {
                be.setColour(0xFFFFFF);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlowColourBlockEntity(ModBlockEntities.FX.get(), pos, state, 0xFFFFFF);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.skyblocks.fx_dye").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A window into the warp: a glowing net of veins drifting in layers, like looking into another dimension. */
    public static class Warp extends FxBlock {
        public static final com.mojang.serialization.MapCodec<Warp> CODEC = simpleCodec(Warp::new);

        public Warp(Properties properties) {
            super(properties, "tooltip.skyblocks.warp_block");
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }
    }

    /** A rift: a jagged black crack hanging in the air, its edges splitting into shimmering red, green and blue. */
    public static class Rift extends FxBlock {
        public static final com.mojang.serialization.MapCodec<Rift> CODEC = simpleCodec(Rift::new);
        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(4, 1, 4, 12, 15, 12);

        public Rift(Properties properties) {
            super(properties, "tooltip.skyblocks.rift");
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                      net.minecraft.world.phys.shapes.CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                               net.minecraft.world.phys.shapes.CollisionContext context) {
            return net.minecraft.world.phys.shapes.Shapes.empty();
        }
    }

    /** TV static: black-and-white noise, new every frame. */
    public static class Static extends FxBlock {
        public static final com.mojang.serialization.MapCodec<Static> CODEC = simpleCodec(Static::new);

        public Static(Properties properties) {
            super(properties, "tooltip.skyblocks.static_block");
        }

        @Override
        protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
            return CODEC;
        }
    }
}
