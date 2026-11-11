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

/** A photo (CameraItem). Right-click a wall: hung there in a frame; the top of a block: a little frame standing on it. */
public class PhotoItem extends Item {
    public PhotoItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ModComponents.Photo photo = ctx.getItemInHand().get(ModComponents.PHOTO.get());
        Direction face = ctx.getClickedFace();
        if (photo == null || face == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        Level level = ctx.getLevel();
        BlockPos at = ctx.getClickedPos().relative(face);
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        if (!level.getBlockState(at).canBeReplaced(place)) {
            return InteractionResult.FAIL;
        }
        boolean standing = face == Direction.UP;
        BlockState state = ModBlocks.PHOTO_FRAME.get().defaultBlockState()
                .setValue(PhotoFrameBlock.FACING, standing ? ctx.getHorizontalDirection().getOpposite() : face)
                .setValue(PhotoFrameBlock.STANDING, standing);
        if (!state.canSurvive(level, at)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(at, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof PhotoFrameBlockEntity be) {
                be.setPhoto(photo);
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
        ModComponents.Photo photo = stack.get(ModComponents.PHOTO.get());
        if (photo != null) {
            tooltip.add(Component.translatable("tooltip.skyblocks.photo.by", photo.by(), photo.day()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.skyblocks.photo").withStyle(ChatFormatting.GRAY));
    }
}
