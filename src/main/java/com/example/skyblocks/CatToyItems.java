package com.example.skyblocks;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** The cat companion's toys you hold: the yarn ball (thrown), the feather wand and the laser pointer. */
public final class CatToyItems {
    private CatToyItems() {}

    /** A ball of yarn: right-click to throw it, and a cat chases it. */
    public static class YarnBall extends Item {
        public YarnBall(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.6F);
            if (!level.isClientSide) {
                YarnBallEntity ball = new YarnBallEntity(ModEntities.YARN_BALL.get(), level);
                Vec3 look = player.getLookAngle();
                ball.setPos(player.getX() + look.x * 0.4, player.getEyeY() - 0.2, player.getZ() + look.z * 0.4);
                ball.setDeltaMovement(look.scale(0.65).add(player.getDeltaMovement().multiply(1, 0, 1)).add(0, 0.12, 0));
                level.addFreshEntity(ball);
            }
            player.getCooldowns().addCooldown(this, 10);
            stack.consume(1, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.skyblocks.yarn_ball").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * A feather on a string on a stick. Held, the feather dangles on the ground in front of you and a cat stalks it;
     * right-click to flick it and the cat pounces.
     */
    public static class FeatherWand extends Item {
        public FeatherWand(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide) {
                FLICKS.put(player.getUUID(), level.getGameTime());
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 1.6F);
            } else {
                clientFlick = level.getGameTime();
            }
            player.getCooldowns().addCooldown(this, 12);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.skyblocks.feather_wand").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Hold right-click: a red dot where you point, and a cat chases it. */
    public static class LaserPointer extends Item {
        public LaserPointer(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 72000;
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.NONE;
        }

        @Override
        public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
            if (!level.isClientSide && user instanceof Player player) {
                BlockHitResult hit = dot(player, 1.0F);
                if (hit != null) {
                    DOTS.put(player.getUUID(), new Dot(hit.getLocation(), level.getGameTime()));
                }
            }
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.skyblocks.laser_pointer").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Where a laser pointer's dot lands (up to 24 blocks), or null. */
    @Nullable
    public static BlockHitResult dot(Player player, float partialTick) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 to = eye.add(player.getViewVector(partialTick).scale(24));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** Where a feather wand's feather dangles: on the ground, a couple of blocks in front of its holder. */
    public static Vec3 feather(Player player, float partialTick) {
        float yaw = Mth.rotLerp(partialTick, player.yRotO, player.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 at = player.getPosition(partialTick).add(-Mth.sin(yaw) * 2.2, 0, Mth.cos(yaw) * 2.2);
        Level level = player.level();
        BlockPos p = BlockPos.containing(at.x, at.y + 1.5, at.z);
        for (int i = 0; i < 5 && level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty(); i++) {
            p = p.below();
        }
        for (int i = 0; i < 3 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) {
            p = p.above();
        }
        double floor = p.getY();
        var below = level.getBlockState(p.below()).getCollisionShape(level, p.below());
        if (!below.isEmpty()) {
            floor = p.getY() - 1 + below.max(net.minecraft.core.Direction.Axis.Y);
        }
        return new Vec3(at.x, floor, at.z);
    }

    public record Dot(Vec3 at, long time) {}

    /** Server: each player's laser dot (and when it was last there); when each last flicked a feather wand. */
    public static final Map<UUID, Dot> DOTS = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> FLICKS = new ConcurrentHashMap<>();
    /** Client: when you last flicked yours. */
    public static long clientFlick = -100;

    /** This player's laser dot, if they're pointing it right now. */
    @Nullable
    static Vec3 dotOf(Player player) {
        Dot d = DOTS.get(player.getUUID());
        if (d == null || player.level().getGameTime() - d.time() > 3 || !(player.getUseItem().getItem() instanceof LaserPointer)) {
            return null;
        }
        return d.at();
    }

    static boolean holdingWand(Player player) {
        return player.getMainHandItem().getItem() instanceof FeatherWand || player.getOffhandItem().getItem() instanceof FeatherWand;
    }
}
