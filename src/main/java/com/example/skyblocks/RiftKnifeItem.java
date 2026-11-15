package com.example.skyblocks;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rift knife: right-click the top of a block with it to cut the ground open - a dark fountain pours up out of the
 * tear (DarkFountainBlock). Cut a fountain (or the ground under it) again to close it. A knife too.
 */
public class RiftKnifeItem extends SwordItem {
    public RiftKnifeItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 2, -1.8F)));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        // a fountain cut (or the ground it pours from): it closes
        BlockPos fountain = level.getBlockState(pos).is(ModBlocks.DARK_FOUNTAIN.get()) ? pos
                : level.getBlockState(pos.above()).is(ModBlocks.DARK_FOUNTAIN.get()) ? pos.above() : null;
        if (fountain != null) {
            if (!level.isClientSide && level.getBlockEntity(fountain) instanceof DarkFountainBlockEntity be) {
                be.close();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos at = pos.above();
        BlockState there = level.getBlockState(at);
        BlockState ground = level.getBlockState(pos);
        if (!there.canBeReplaced() || !there.getFluidState().isEmpty() || !ground.isFaceSturdy(level, pos, Direction.UP)) {
            return InteractionResult.PASS;
        }
        // (one fountain at a time near here: not two pouring into each other)
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 2, 3))) {
            if (level.getBlockState(p).is(ModBlocks.DARK_FOUNTAIN.get())) {
                if (player != null && !level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.skyblocks.rift_knife.near"), true);
                }
                return InteractionResult.FAIL;
            }
        }
        if (!level.isClientSide) {
            level.setBlock(at, ModBlocks.DARK_FOUNTAIN.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof DarkFountainBlockEntity be) {
                be.open();
            }
            level.playSound(null, at, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 0.5F, 0.5F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SQUID_INK, at.getX() + 0.5, at.getY() + 0.05, at.getZ() + 0.5, 20, 0.5, 0.02, 0.5, 0.05);
            }
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(context.getHand()));
                player.getCooldowns().addCooldown(this, 40);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.rift_knife").withStyle(ChatFormatting.GRAY));
    }
}
