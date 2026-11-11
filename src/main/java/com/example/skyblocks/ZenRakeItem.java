package com.example.skyblocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A little wooden rake for zen sand. Right-click: lines the way you're facing (hold it and sweep across the sand to
 * rake a whole row); the same block again: waves, then smooth. Sneak: a ring round that block.
 */
public class ZenRakeItem extends Item {
    /** Each player's last raked block and when: holding the button rakes on, it doesn't keep changing the same block. */
    private record Last(BlockPos pos, long time) {}

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();

    public ZenRakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        if (!(state.getBlock() instanceof ZenSandBlock) || player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        long now = level.getGameTime();
        Last last = LAST.get(player.getUUID());
        boolean same = last != null && last.pos.equals(pos);
        LAST.put(player.getUUID(), new Last(pos.immutable(), now));
        if (same && now - last.time <= 5) {
            return InteractionResult.CONSUME; // (the button held down on the same block)
        }
        if (player.isShiftKeyDown()) {
            ZenSandBlock.ring(level, pos);
        } else {
            ZenSandBlock.Pattern now0 = state.getValue(ZenSandBlock.PATTERN);
            ZenSandBlock.Pattern next = ZenSandBlock.next(now0, ctx.getHorizontalDirection().getAxis(), same);
            if (next == now0) {
                return InteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(ZenSandBlock.PATTERN, next), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.BRUSH_SAND, SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5,
                    6, 0.3, 0.02, 0.3, 0.02);
        }
        ctx.getItemInHand().hurtAndBreak(1, player, ctx.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.zen_rake").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skyblocks.zen_rake.ring").withStyle(ChatFormatting.GRAY));
    }
}
