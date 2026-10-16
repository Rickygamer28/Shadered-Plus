package com.example.zinogre;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Roars when a fight starts and charges up (see {@link ZinogreEntity}), then chases the target: leaps at it from a
 * distance, and once it is in reach stops and bites, or now and then does the backflip tail slam. It stands still while
 * attacking and only turns towards the target during the wind-up, so the target can dodge.
 */
class ZinogreAttackGoal extends Goal {
    private static final double CHASE_SPEED = 1.0;
    /** Wind-up ticks during which it keeps turning towards the target. */
    private static final int AIM_TICKS = 9;

    private final ZinogreEntity mob;
    private int cooldown;
    private int repath;

    ZinogreAttackGoal(ZinogreEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !mob.isOrderedToSit();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || mob.isBusy();
    }

    @Override
    public void start() {
        repath = 0;
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
        LivingEntity target = mob.getTarget();
        if (mob.isCharging()) {
            mob.getNavigation().stop();
            if (target != null) {
                face(target, 10.0F);
            }
            return;
        }
        if (mob.isAttacking()) {
            mob.getNavigation().stop();
            if (target != null && mob.attackTick < AIM_TICKS) {
                face(target, 20.0F);
            }
            return;
        }
        if (mob.isDoingMove()) {
            mob.getNavigation().stop();
            // roaring: keeps facing the target; leap: aims during the crouch; flip: committed
            if (target != null && (mob.move == ZinogreEntity.MOVE_ROAR
                    || (mob.move == ZinogreEntity.MOVE_LEAP && mob.moveTick < 4))) {
                face(target, 15.0F);
            }
            return;
        }
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (cooldown > 0) {
            cooldown--;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        // charges up at the start of a fight (and again once the last charge has worn off and cooled down)
        if (mob.canCharge() && dx * dx + dz * dz < 20.0 * 20.0 && mob.hasLineOfSight(target)) {
            face(target, 180.0F);
            mob.startCharge();
            return;
        }
        double reach = mob.getBbWidth() * 0.5 + 3.0 * mob.sizeScale() + target.getBbWidth() * 0.5;
        boolean inReach = dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - mob.getY()) < 3.0;
        if (inReach && cooldown <= 0 && mob.hasLineOfSight(target)) {
            mob.getNavigation().stop();
            // now and then the backflip tail slam instead of the bite (more often when the target is beside or
            // behind it, where the bite can't reach)
            float off = Math.abs(Mth.degreesDifference(mob.getYRot(),
                    (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F));
            if (mob.canFlip() && mob.getRandom().nextFloat() < (off > 70.0F ? 0.7F : 0.3F)) {
                mob.startMove(ZinogreEntity.MOVE_FLIP, target);
                cooldown = 25 + mob.getRandom().nextInt(20);
                return;
            }
            face(target, 180.0F);
            mob.startAttack();
            cooldown = 15 + mob.getRandom().nextInt(20);
            return;
        }
        // far away (but not too far) and on the same level: leaps at it
        double distSq = dx * dx + dz * dz;
        double far = mob.isAlpha() ? 1.8 : 1.0; // an Alpha leaps from farther away
        if (!inReach && distSq > 49.0 * far * far && distSq < 289.0 * far * far && Math.abs(target.getY() - mob.getY()) < 4.0 * far
                && mob.canLeap() && mob.hasLineOfSight(target) && mob.getRandom().nextInt(10) == 0) {
            face(target, 180.0F);
            mob.startMove(ZinogreEntity.MOVE_LEAP, target);
            return;
        }
        if (--repath <= 0 || mob.getNavigation().isDone()) {
            repath = 8 + mob.getRandom().nextInt(6);
            if (!inReach) {
                mob.getNavigation().moveTo(target, CHASE_SPEED);
            } else {
                mob.getNavigation().stop();
            }
        }
    }

    private void face(LivingEntity target, float maxTurn) {
        float want = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(mob.getYRot(), want, maxTurn);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
    }
}
