package com.example.skyblocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sky Jellyfish: drifts in slow loose groups high in the night sky, its bell pulsing. Touch one and you float up for a
 * moment, then fall slowly. A glass bottle scoops a little Sky Jelly off it (now and then).
 */
public class SkyJellyfish extends SkyCreature {
    private Vec3 target;
    private int retarget;
    private int jellyCooldown;
    private final Map<UUID, Long> touched = new HashMap<>();

    public SkyJellyfish(EntityType<? extends SkyJellyfish> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.MOVEMENT_SPEED, 0.1)
                .add(Attributes.FLYING_SPEED, 0.1);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Vec3 base = home != null ? home : position();
        if (target == null || --retarget <= 0 || target.distanceToSqr(position()) < 1.0) {
            target = base.add((random.nextDouble() - 0.5) * 20.0, (random.nextDouble() - 0.5) * 6.0, (random.nextDouble() - 0.5) * 20.0);
            retarget = 120 + random.nextInt(120);
        }
        steer(target, 0.035, 0.03);
        // (a pulse pushes it up a little; it sinks gently between)
        double pulse = Math.sin(tickCount * 0.12);
        setDeltaMovement(getDeltaMovement().add(0.0, pulse > 0.85 ? 0.004 : -0.0012, 0.0));
        if (jellyCooldown > 0) {
            jellyCooldown--;
        }
        // touch: float up a moment, then fall slowly
        long now = level().getGameTime();
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.2))) {
            Long last = touched.get(player.getUUID());
            if (last == null || now - last > 60) {
                touched.put(player.getUUID(), now);
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0));
                level().playSound(null, blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.6F, 1.6F);
            }
        }
        if (touched.size() > 16) {
            touched.clear();
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (jellyCooldown > 0) {
                return InteractionResult.PASS;
            }
            if (!level().isClientSide) {
                jellyCooldown = 1200;
                ItemStack jelly = new ItemStack(ModItems.SKY_JELLY.get());
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, jelly));
                level().playSound(null, blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.2F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH_SMALL;
    }
}
