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
    private static final double CHASE_SPEED = 1.2;
    /** Wind-up ticks during which it keeps turning towards the target. */
    private static final int AIM_TICKS = 9;

    private final ZinogreEntity mob;
    private int cooldown;
    private int repath;
    /** Alpha: above 0 walks straight at the target; below 0 counts up the ticks it uses a path after getting stuck. */
    private int direct = 1;
    /** Decided to leap, turning to face the target first. */
    private boolean leapReady;
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.phys.Vec3 lastPos;

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
                    || (mob.move == ZinogreEntity.MOVE_LEAP && mob.moveTick < ZinogreEntity.LEAP_LOCK))) {
                face(target, mob.move == ZinogreEntity.MOVE_LEAP ? 30.0F : 15.0F);
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
            if (turnedTo(target)) {
                mob.startCharge();
            }
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
            if (turnedTo(target)) {
                mob.startAttack();
                cooldown = 15 + mob.getRandom().nextInt(20);
            }
            return;
        }
        // far away (but not too far) and on the same level: leaps at it
        double distSq = dx * dx + dz * dz;
        double far = mob.isAlpha() ? 1.8 : 1.0; // an Alpha leaps from farther away
        if (!inReach && distSq > 49.0 * far * far && distSq < 289.0 * far * far && Math.abs(target.getY() - mob.getY()) < 4.0 * far
                && mob.canLeap() && mob.hasLineOfSight(target) && (leapReady || mob.getRandom().nextInt(10) == 0)) {
            leapReady = true; // decided: turns to face it first if it has to
            if (turnedTo(target)) {
                leapReady = false;
                mob.startMove(ZinogreEntity.MOVE_LEAP, target);
            }
            return;
        }
        leapReady = false;
        // an Alpha walks straight at a target it can see (paths are made of steps far too small for it to follow
        // smoothly), unless it's got stuck that way; then it finds a path for a while
        if (mob.isAlpha() && !inReach && direct > 0 && mob.hasLineOfSight(target) && Math.abs(target.getY() - mob.getY()) < 4.0) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), CHASE_SPEED);
            if (mob.tickCount % 20 == 0) {
                boolean stuck = lastPos != null && mob.position().distanceToSqr(lastPos) < 0.5 * 0.5;
                lastPos = mob.position();
                if (stuck) {
                    direct = -60; // pathfinding for the next 3 seconds
                }
            }
            return;
        }
        if (direct <= 0 && ++direct == 0) {
            direct = 1;
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

    /** Turns toward the target (smoothly, see ZinogreEntity#turnTowards), looking at it. */
    private void face(LivingEntity target, float maxTurn) {
        mob.turnTowards(yawTo(target), maxTurn);
        mob.yBodyRot = mob.getYRot();
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private float yawTo(LivingEntity target) {
        return (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /**
     * Whether it's facing the target closely enough to start a move at it; if not it turns toward it this tick (and
     * stops walking) instead of snapping round.
     */
    private boolean turnedTo(LivingEntity target) {
        if (Math.abs(Mth.wrapDegrees(yawTo(target) - mob.getYRot())) <= 25.0F) {
            return true;
        }
        mob.getNavigation().stop();
        face(target, mob.isAlpha() ? 12.0F : 25.0F);
        return false;
    }
}
