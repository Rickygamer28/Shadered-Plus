package com.example.skyblocks;

import com.example.skyblocks.island.ShaderedSkyItems;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The sky wand: hold right-click and draw lines of sky in the air (SkyTrailEntity), a little way in front of you. The
 * sky is the sky block in your other hand (remembered for next time; Space to begin with). Sneak + right-click: lines
 * that fade after a minute, or lines that stay. Left-click a line you drew (with the wand) to rub it out.
 */
public class SkyWandItem extends Item {
    /** How far in front of the eye the line is drawn, and how far apart its points are at least. */
    private static final double REACH = 2.4, SPACING = 0.08;
    private static final Map<Player, SkyTrailEntity> DRAWING = new WeakHashMap<>();

    public SkyWandItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean keeps(ItemStack stack) {
        return data(stack).getBoolean("keep");
    }

    /** The wand's sky (Shadered's sky type and pass, by name). */
    public static String[] sky(ItemStack stack) {
        CompoundTag t = data(stack);
        String sky = t.getString("sky"), pass = t.getString("pass");
        return new String[] {sky.isEmpty() ? "SPACE" : sky, pass.isEmpty() ? "NORMAL" : pass};
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean keep = !keeps(stack);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean("keep", keep));
                player.displayClientMessage(Component.translatable(keep ? "message.skyblocks.sky_wand.keep" : "message.skyblocks.sky_wand.fade"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, keep ? 1.4F : 0.9F);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // the sky in the other hand, if it's a sky block: the wand's from now on
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        ShaderedSkyItems.Sky sky = ShaderedSkyItems.sky(other);
        if (sky != null && !level.isClientSide) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                t.putString("sky", sky.type().name());
                t.putString("pass", sky.pass().name());
            });
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
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
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        Vec3 tip = player.getEyePosition().add(player.getLookAngle().scale(REACH));
        SkyTrailEntity line = DRAWING.get(player);
        if (line == null || line.isRemoved() || line.level() != level) {
            String[] sky = sky(stack);
            line = SkyTrailEntity.start(level, tip, sky[0], sky[1], keeps(stack), player.getUUID());
            level.addFreshEntity(line);
            DRAWING.put(player, line);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.6F);
            return;
        }
        if (line.last().distanceToSqr(tip) < SPACING * SPACING) {
            return;
        }
        if (!line.add(tip)) {
            // (full: the stroke carries on in a new line, from where this one ends)
            Vec3 from = line.last();
            line.finish();
            String[] sky = sky(stack);
            line = SkyTrailEntity.start(level, from, sky[0], sky[1], keeps(stack), player.getUUID());
            line.add(tip);
            level.addFreshEntity(line);
            DRAWING.put(player, line);
        }
        if (level instanceof ServerLevel server && player.tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 1, 0.03, 0.03, 0.03, 0.005);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        stop(entity);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        stop(entity);
    }

    private static void stop(LivingEntity entity) {
        if (entity instanceof Player player && !player.level().isClientSide) {
            SkyTrailEntity line = DRAWING.remove(player);
            if (line != null && !line.isRemoved()) {
                line.finish();
            }
        }
    }

    /** A left-click with the wand (EraseTrailPayload): the player's own lines it's pointed at rubbed out. */
    public static void erase(Player player) {
        if (!(player.getMainHandItem().getItem() instanceof SkyWandItem)) {
            return;
        }
        Vec3 eye = player.getEyePosition(), dir = player.getLookAngle();
        double length = 24.0;
        int n = 0;
        for (SkyTrailEntity line : player.level().getEntitiesOfClass(SkyTrailEntity.class, player.getBoundingBox().inflate(length))) {
            boolean mine = player.getUUID().equals(line.owner()) || player.hasPermissions(2);
            if (mine && line.near(eye, dir, length, 0.5)) {
                if (player.level() instanceof ServerLevel server) {
                    Vec3 at = line.last();
                    server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.02);
                }
                line.discard();
                n++;
            }
        }
        if (n > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.6F, 1.5F);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skyblocks.sky_wand").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(keeps(stack) ? "tooltip.skyblocks.sky_wand.keep" : "tooltip.skyblocks.sky_wand.fade")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
