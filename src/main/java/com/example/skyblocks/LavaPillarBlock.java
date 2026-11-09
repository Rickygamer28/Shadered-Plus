package com.example.skyblocks;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A lava lamp pillar: a glass column of coloured liquid with wax blobs rising and sinking through it. Stacked, they make
 * one tall lamp (a metal foot at the bottom, a cap at the top), the blobs travelling its whole height
 * (client/LavaPillarRenderer). As a lava lamp: right-click on / off, sneak + right-click RGB, a dye the wax's colour; and
 * sneak + a dye: the liquid's - for the whole pillar.
 */
public class LavaPillarBlock extends BaseEntityBlock {
    public static final MapCodec<LavaPillarBlock> CODEC = simpleCodec(LavaPillarBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** (the tallest pillar drawn as one) */
    public static final int MAX_HEIGHT = 16;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public enum Part implements StringRepresentable {
        SINGLE, BOTTOM, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public LavaPillarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.SINGLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // (its foot and cap; the glass and what's in it: its renderer)
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private Part part(LevelAccessor level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this), above = level.getBlockState(pos.above()).is(this);
        return below ? (above ? Part.MIDDLE : Part.TOP) : (above ? Part.BOTTOM : Part.SINGLE);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(PART, part(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState next, LevelAccessor level, BlockPos pos, BlockPos nextPos) {
        return dir.getAxis().isVertical() ? state.setValue(PART, part(level, pos)) : state;
    }

    /** The bottom of the pillar this block is part of. */
    public static BlockPos bottom(BlockGetter level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i < 64 && level.getBlockState(p.below()).getBlock() instanceof LavaPillarBlock; i++) {
            p = p.below();
        }
        return p;
    }

    /** How many blocks tall the pillar is from its bottom. */
    public static int height(BlockGetter level, BlockPos bottom) {
        int h = 1;
        while (h < 64 && level.getBlockState(bottom.above(h)).getBlock() instanceof LavaPillarBlock) {
            h++;
        }
        return h;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            int colour = dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
            BlockPos b = bottom(level, pos);
            int h = height(level, b);
            for (int i = 0; i < h; i++) {
                if (level.getBlockEntity(b.above(i)) instanceof LavaPillarBlockEntity be) {
                    if (player.isShiftKeyDown()) {
                        be.setLiquid(colour);
                    } else {
                        be.setWax(colour);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Right-click: on / off; sneaking: RGB (the wax cycling through the rainbow) - for the whole pillar, as a lava lamp. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(bottom(level, pos)) instanceof LavaPillarBlockEntity base) {
            boolean rgb = player.isShiftKeyDown() ? !base.rgb() : base.rgb();
            boolean lit = player.isShiftKeyDown() ? base.lit() : !base.lit();
            BlockPos b = bottom(level, pos);
            int h = height(level, b);
            for (int i = 0; i < h; i++) {
                if (level.getBlockEntity(b.above(i)) instanceof LavaPillarBlockEntity be) {
                    be.setLit(lit);
                    be.setRgb(rgb);
                }
            }
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable(rgb ? "message.skyblocks.rgb_on" : "message.skyblocks.rgb_off"), true);
            } else {
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, lit ? 1.0F : 0.7F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A new block on a pillar takes its colours. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LavaPillarBlockEntity be) {
            for (BlockPos n : new BlockPos[] {pos.below(), pos.above()}) {
                if (level.getBlockEntity(n) instanceof LavaPillarBlockEntity other) {
                    be.setWax(other.wax());
                    be.setLiquid(other.liquid());
                    be.setRgb(other.rgb());
                    be.setLit(other.lit());
                    return;
                }
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LavaPillarBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.lava_pillar").withStyle(ChatFormatting.GRAY));
    }
}
