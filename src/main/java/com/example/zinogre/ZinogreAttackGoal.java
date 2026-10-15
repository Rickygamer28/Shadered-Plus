package com.example.zinogre;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Charges up when a fight starts (see {@link ZinogreEntity}), then chases the target and, once it is in reach, stops
 * and does the lunge attack. It stands
 * still while attacking and only turns towards the target during the wind-up, so the target can dodge the lunge.
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
        return canUse() || mob.isAttacking() || mob.isCharging();
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
        double reach = mob.getBbWidth() * 0.5 + 3.0 + target.getBbWidth() * 0.5;
        boolean inReach = dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - mob.getY()) < 3.0;
        if (inReach && cooldown <= 0 && mob.hasLineOfSight(target)) {
            mob.getNavigation().stop();
            face(target, 180.0F);
            mob.startAttack();
            cooldown = 15 + mob.getRandom().nextInt(20);
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
