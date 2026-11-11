package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** An empty photo frame (PhotoFrameBlock) of one size: right-click a wall to hang it, the top of a block to stand it there. */
public class PhotoFrameItem extends Item {
    private final PhotoFrameBlock.Size size;

    public PhotoFrameItem(PhotoFrameBlock.Size size, Properties properties) {
        super(properties);
        this.size = size;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        Level level = ctx.getLevel();
        BlockPos at = ctx.getClickedPos().relative(face);
        if (!level.getBlockState(at).canBeReplaced(new BlockPlaceContext(ctx))) {
            return InteractionResult.FAIL;
        }
        boolean standing = face == Direction.UP;
        Direction facing = standing ? ctx.getHorizontalDirection().getOpposite() : face;
        BlockState state = ModBlocks.PHOTO_FRAME.get().defaultBlockState().setValue(PhotoFrameBlock.FACING, facing)
                .setValue(PhotoFrameBlock.STANDING, standing).setValue(PhotoFrameBlock.SIZE, size);
        if (!state.canSurvive(level, at) || !PhotoFrameBlock.roomFor(level, at, facing, standing, size)) {
            if (level.isClientSide && ctx.getPlayer() != null) {
                ctx.getPlayer().displayClientMessage(Component.translatable("message.skyblocks.photo_frame.room").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(at, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PhotoFrameBlockEntity be) {
                be.setFramed(true);
            }
            level.playSound(null, at, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            Player player = ctx.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.photo_frame." + size.getSerializedName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.photo_frame.use").withStyle(ChatFormatting.GRAY));
    }
}
