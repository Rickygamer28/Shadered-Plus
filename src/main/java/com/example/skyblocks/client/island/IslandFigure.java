package com.example.skyblocks.client.island;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.tags.FluidTags;
import com.example.skyblocks.mixin.EntityAccessor;

/**
 * A mini copy of a scanned mob: a client-only entity (never added to the world: no AI, no drops, can't be hit) that
 * is drawn with its normal renderer. Mobs that stood on the ground wander around the island now and then and look
 * around while they rest; the others (flying, swimming, or pets told to sit) stay where they were and look around.
 */
final class IslandFigure {
    private static final double SPEED = 0.06; // blocks per tick, in island blocks
    private static final float DEG = Mth.DEG_TO_RAD;

    final Entity entity;
    private double x, y, z;
    private double xo, yo, zo;
    private final boolean walker;
    private double targetX, targetZ;
    private int idle;
    private float body;
    private final float phase;
    /** The first target is picked on the first tick (the island isn't known in the constructor's caller order). */
    private boolean firstRest;

    IslandFigure(Entity entity, double x, double y, double z, RandomSource random, IslandView island) {
        this.entity = entity;
        this.x = this.xo = x;
        this.y = this.yo = y;
        this.z = this.zo = z;
        this.targetX = x;
        this.targetZ = z;
        this.body = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
        // pets that were sitting (told to stay) when scanned stay where they are
        boolean sitting = entity instanceof TamableAnimal pet && (pet.isInSittingPose() || pet.isOrderedToSit());
        this.walker = entity instanceof Mob && !(entity instanceof FlyingMob) && entity.onGround() && !sitting;
        this.idle = 20 + random.nextInt(120);
        this.firstRest = true;
        this.phase = random.nextFloat() * 100.0F;
        entity.setYRot(body);
        entity.yRotO = body;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = body;
            living.yHeadRot = living.yHeadRotO = body;
        }
        // Copies never tick, so they don't know they're in water: fish would be drawn flopping on their side.
        boolean inWater = entity instanceof WaterAnimal || island.stateAt((int) Math.floor(x), (int) Math.floor(y + 0.2),
                (int) Math.floor(z)).getFluidState().is(FluidTags.WATER);
        if (inWater) {
            ((EntityAccessor) entity).skyblocks$setWasTouchingWater(true);
        }
    }

    void tick(IslandView island, RandomSource random) {
        xo = x;
        yo = y;
        zo = z;
        entity.tickCount++;
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.yBodyRotO = living.yBodyRot;
        living.yHeadRotO = living.yHeadRot;
        living.yRotO = living.getYRot();
        living.xRotO = living.getXRot();

        if (walker && firstRest) {
            firstRest = false;
            rest(island, random, idle);
        }
        float moved = 0;
        if (walker && idle <= 0) {
            double dx = targetX - x, dz = targetZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.25) {
                rest(island, random, 40 + random.nextInt(160)); // arrived: look around for a while
            } else {
                float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                body = Mth.approachDegrees(body, want, 15.0F);
                // turn first, walk once roughly facing the target
                if (Math.abs(Mth.degreesDifference(body, want)) < 35.0F) {
                    double step = Math.min(SPEED, dist);
                    double nx = x - Mth.sin(body * DEG) * step;
                    double nz = z + Mth.cos(body * DEG) * step;
                    double floor = standable(island, nx, nz, y);
                    if (Double.isNaN(floor)) {
                        rest(island, random, 10 + random.nextInt(20)); // blocked after all: pick another spot soon
                    } else {
                        x = nx;
                        z = nz;
                        y = floor;
                        moved = (float) step;
                    }
                }
            }
        } else if (walker) {
            idle--;
        }

        if (entity instanceof Mob) {
            float t = entity.tickCount + phase;
            boolean resting = moved == 0;
            living.yBodyRot = body;
            living.setYRot(body);
            living.yHeadRot = body + (resting ? 40.0F * Mth.sin(t * 0.045F) : 0.0F);
            living.setXRot(resting ? 12.0F * Mth.sin(t * 0.031F) : 0.0F);
            living.walkAnimation.update(Math.min(moved * 4.0F, 1.0F), 0.4F);
        }
    }

    /**
     * Stops for the given time and picks the next spot to walk to: a random spot on the island that can be reached
     * in a straight line without walking off an edge, into a wall or down a big drop. If none is found the mob just
     * stays and looks around.
     */
    private void rest(IslandView island, RandomSource random, int ticks) {
        idle = ticks;
        targetX = x;
        targetZ = z;
        for (int attempt = 0; attempt < 12; attempt++) {
            double tx = x + (random.nextDouble() - 0.5) * 12;
            double tz = z + (random.nextDouble() - 0.5) * 12;
            if (reachable(island, tx, tz)) {
                targetX = tx;
                targetZ = tz;
                return;
            }
        }
    }

    /** Whether the straight line from here to (tx, tz) has floor all the way (checked every half block). */
    private boolean reachable(IslandView island, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0) {
            return false;
        }
        int steps = (int) Math.ceil(dist / 0.5);
        double height = y;
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double floor = standable(island, x + dx * f, z + dz * f, height);
            if (Double.isNaN(floor)) {
                return false;
            }
            height = floor;
        }
        return true;
    }

    /** Floor height a mob at height y can step onto at (px, pz), or NaN (off the island, wall, or big drop). */
    private static double standable(IslandView island, double px, double pz, double y) {
        if (px < 0.4 || pz < 0.4 || px > island.size - 0.4 || pz > island.size - 0.4) {
            return Double.NaN;
        }
        double floor = island.floorBelow(px, y, pz);
        if (Double.isNaN(floor) || floor > y + 1.05 || floor < y - 3.0) {
            return Double.NaN;
        }
        return floor;
    }

    /**
     * Draws the figure on its own copy of the pose, so a renderer that fails half-way can't leave the shared
     * PoseStack unbalanced. Returns false if it failed (the figure is then dropped).
     */
    boolean render(IslandView island, EntityRenderDispatcher dispatcher, PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        double rx = Mth.lerp(partialTick, xo, x);
        double ry = Mth.lerp(partialTick, yo, y);
        double rz = Mth.lerp(partialTick, zo, z);
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        int light = island.lightAt(rx, ry + 0.5, rz);
        PoseStack own = new PoseStack();
        own.last().pose().set(poseStack.last().pose());
        own.last().normal().set(poseStack.last().normal());
        try {
            dispatcher.render(entity, rx, ry, rz, yaw, partialTick, own, buffer, light);
            return true;
        } catch (RuntimeException e) {
            return false; // a mob whose renderer needs it to be in the world
        }
    }
}
