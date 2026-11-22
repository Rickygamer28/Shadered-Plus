package com.example.skyblocks;

import com.noodlegamer76.shadered.client.util.SkyblockType;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import org.jetbrains.annotations.Nullable;

/**
 * Sky Moth: flutters round sky lanterns (and the sky things near them) at night, its wings showing the sky of the
 * nearest one. Scatters when a player comes close, comes back when they stand still. A glass bottle catches one.
 */
public class SkyMoth extends SkyCreature {
    private Vec3 target;
    private int retarget;
    private int scared;
    @Nullable
    private Vec3 lastPlayerPos;

    public SkyMoth(EntityType<? extends SkyMoth> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 2.0).add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.2);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Vec3 base = home != null ? home : position();
        Player near = level().getNearestPlayer(this, 3.0);
        if (near != null && !near.isSpectator()) {
            Vec3 p = near.position();
            boolean moving = lastPlayerPos != null && lastPlayerPos.distanceToSqr(p) > 0.002;
            lastPlayerPos = p;
            if (moving) {
                scared = 60;
                Vec3 away = position().subtract(p);
                target = position().add(away.normalize().scale(5.0)).add(0, 1.0, 0);
                retarget = 30;
            }
        } else {
            lastPlayerPos = null;
        }
        if (scared > 0) {
            scared--;
        }
        if (target == null || --retarget <= 0 || target.distanceToSqr(position()) < 0.1) {
            target = base.add((random.nextDouble() - 0.5) * 4.0, (random.nextDouble() - 0.3) * 2.5, (random.nextDouble() - 0.5) * 4.0);
            retarget = 10 + random.nextInt(25);
        }
        steer(target, scared > 0 ? 0.18 : 0.09, 0.25);
        // (a moth's flight is never smooth)
        setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.03, (random.nextDouble() - 0.5) * 0.03,
                (random.nextDouble() - 0.5) * 0.03));
        // its wings take the sky of the nearest sky lantern
        if (tickCount % 100 == 0) {
            SkyblockType sky = nearestLanternSky();
            if (sky != null) {
                setSky(sky);
            }
        }
    }

    @Nullable
    private SkyblockType nearestLanternSky() {
        BlockPos at = blockPosition();
        double best = Double.MAX_VALUE;
        SkyblockType found = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -4, -6), at.offset(6, 4, 6))) {
            if (level().getBlockEntity(p) instanceof SkyLanternBlockEntity lantern) {
                double d = p.distSqr(at);
                if (d < best) {
                    SkyblockType sky = SkyCreatureSpawner.skyNamed(lantern.sky());
                    if (sky != null) {
                        best = d;
                        found = sky;
                    }
                }
            }
        }
        return found;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!level().isClientSide) {
                ItemStack bottled = new ItemStack(ModItems.BOTTLED_SKY_MOTH.get());
                bottled.set(ModComponents.SKY_BRUSH.get(), sky().name());
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, bottled));
                level().playSound(null, blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.6F);
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
