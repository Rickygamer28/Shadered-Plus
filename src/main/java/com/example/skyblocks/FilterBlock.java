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
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * A filter block (from The Valley Mod by D0nkey100, used with permission): see-through, the world behind it seen through
 * its filter - inverted, posterized, in grey, split into colours, as a blueprint, on a Game Boy, in big pixels, in cross
 * stitch, through a red lens or a tinted one, or on a screen of little lights (client/FilterRenderer,
 * shaders/core/filter_glass). Blocks of one filter side by side make one pane; stand inside one and it's all you see.
 * The blueprint and tint filters take a dye's colour (right-click with a dye).
 */
public class FilterBlock extends BaseEntityBlock {
    public static final MapCodec<FilterBlock> CODEC = simpleCodec(p -> new FilterBlock(1, false, p));

    /** Its filter (TVM's effect numbers: shaders/core/filter_glass), and whether it takes a dye's colour. */
    private final int mode;
    private final boolean dyeable;

    public FilterBlock(int mode, boolean dyeable, Properties properties) {
        super(properties);
        this.mode = mode;
        this.dyeable = dyeable;
    }

    public int mode() {
        return mode;
    }

    public boolean dyeable() {
        return dyeable;
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
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** Next to one of the same filter: the face between them not drawn (one pane). */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!dyeable || !(stack.getItem() instanceof DyeItem dye) || !(level.getBlockEntity(pos) instanceof FilterBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            be.setColour(dye.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilterBlockEntity(pos, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.filter").withStyle(ChatFormatting.GRAY));
        if (dyeable) {
            tooltip.add(Component.translatable("tooltip.skyblocks.filter.dye").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skyblocks.tvm_credit").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }
}
