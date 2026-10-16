package com.example.zinogre;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A tamed Zinogre keeps up with its owner: it follows once the owner is a few body lengths away (vanilla's follow only
 * starts at 12 blocks and loses a mob this big), walks straight to them when it can see them, takes a path when it
 * can't, hurries when it has fallen behind, and appears next to them when it's very far (or stuck).
 */
public class ZinogreFollowOwnerGoal extends Goal {
    private final ZinogreEntity mob;
    private int repath;
    private int stuckTicks;
    private Vec3 lastPos = Vec3.ZERO;

    public ZinogreFollowOwnerGoal(ZinogreEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private double start() {
        return mob.getBbWidth() * 0.5 + 5.0;
    }

    private double stop() {
        return mob.getBbWidth() * 0.5 + 2.0;
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = mob.getOwner();
        return mob.isTame() && owner != null && owner.isAlive() && !owner.isSpectator() && !mob.isOrderedToSit()
                && !mob.isLeashed() && !mob.isPassenger() && mob.getTarget() == null && !mob.isBusy()
                && mob.distanceToSqr(owner) > start() * start() && owner.level() == mob.level();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity owner = mob.getOwner();
        return owner != null && owner.isAlive() && !owner.isSpectator() && !mob.isOrderedToSit() && mob.getTarget() == null
                && owner.level() == mob.level() && mob.distanceToSqr(owner) > stop() * stop();
    }

    @Override
    public void start() {
        repath = 0;
        stuckTicks = 0;
        lastPos = mob.position();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity owner = mob.getOwner();
        if (owner == null) {
            return;
        }
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        double dist = Math.sqrt(mob.distanceToSqr(owner));
        // very far behind, or stuck for a while: appear next to the owner
        if (mob.tickCount % 20 == 0) {
            stuckTicks = mob.position().distanceToSqr(lastPos) < 0.25 ? stuckTicks + 20 : 0;
            lastPos = mob.position();
        }
        if (dist > 24.0 || (stuckTicks >= 60 && dist > 8.0)) {
            if (teleportNear(owner)) {
                stuckTicks = 0;
                return;
            }
        }
        double speed = dist > 12.0 ? 1.45 : 1.15;
        if (mob.hasLineOfSight(owner) && Math.abs(owner.getY() - mob.getY()) < 2.5 && dist < 16.0) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(owner.getX(), owner.getY(), owner.getZ(), speed);
            return;
        }
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = 10;
            mob.getNavigation().moveTo(owner, speed);
        }
    }

    /** Tries a few spots around the owner where it fits, standing on something solid. */
    private boolean teleportNear(LivingEntity owner) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 16; i++) {
            int dx = mob.getRandom().nextIntBetweenInclusive(-5, 5), dz = mob.getRandom().nextIntBetweenInclusive(-5, 5);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos at = base.offset(dx, dy, dz);
                if (!mob.level().getBlockState(at.below()).isFaceSturdy(mob.level(), at.below(), net.minecraft.core.Direction.UP)) {
                    continue;
                }
                Vec3 to = new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                if (mob.level().noCollision(mob, mob.getBoundingBox().move(to.subtract(mob.position())))) {
                    mob.moveTo(to.x, to.y, to.z, mob.getYRot(), mob.getXRot());
                    mob.getNavigation().stop();
                    return true;
                }
            }
        }
        return false;
    }
}
