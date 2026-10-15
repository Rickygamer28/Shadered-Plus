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
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import com.example.skyblocks.mixin.EntityAccessor;

/**
 * A mini copy of a scanned mob: a client-only entity (never added to the world: no AI, no drops, can't be hit) that
 * is drawn with its normal renderer. Mobs that stood on the ground wander around the island now and then and look
 * around while they rest; the others (flying, swimming, or pets told to sit) stay where they were and look around.
 *
 * The island only decides where a mob goes. Its animations are the mob's own: every island tick the copy is moved and
 * then ticked like a mob seen from a client (no AI, no physics), so its own code works out how it animates from that
 * movement - legs, wings, slime squish, GeckoLib and other modded animations alike. While ticking, the copy sits high
 * above the build limit (moving along with its island position), so nothing it does touches the world: no blocks
 * there, its particles are out of sight and client copies make no sounds. For drawing it is put back at the island.
 * A mob whose tick fails falls back to the island's simple walk animation.
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
    /** Slimes (and magma cubes) get around by jumping: height above the floor and ticks into the jump (-1: landed). */
    private final boolean slime;
    private double hop, hopO;
    private int hopTick = -1;
    private static final int HOP_TICKS = 10;
    private static final double HOP_HEIGHT = 0.7, HOP_SPEED = 0.18;
    /** Where the copy is drawn from (the island's block), and the height it is ticked at. */
    private final Vec3 display;
    private final double tickHeight;
    /** Whether the mob's own tick drives its animation (false once it failed). */
    private boolean ownTick = true;
    private final boolean grounded, inWater;

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
        this.slime = entity instanceof Slime;
        this.idle = 20 + random.nextInt(120);
        this.firstRest = true;
        this.phase = random.nextFloat() * 100.0F;
        entity.setYRot(body);
        entity.yRotO = body;
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = living.yBodyRotO = body;
            living.yHeadRot = living.yHeadRotO = body;
        }
        // The copy isn't really in the island's water: fish would be drawn flopping on their side.
        this.inWater = entity instanceof WaterAnimal || island.stateAt((int) Math.floor(x), (int) Math.floor(y + 0.2),
                (int) Math.floor(z)).getFluidState().is(FluidTags.WATER);
        if (inWater) {
            ((EntityAccessor) entity).skyblocks$setWasTouchingWater(true);
        }
        this.grounded = entity.onGround();
        this.display = entity.position();
        this.tickHeight = entity.level().getMaxBuildHeight() + 64.0;
        entity.setPos(tickPos(x, y, z));
        entity.setOldPosAndRot();
    }

    /** Where the copy is while it ticks: above the build limit, moving with its island position. */
    private Vec3 tickPos(double ix, double iy, double iz) {
        return new Vec3(display.x + ix, tickHeight + iy, display.z + iz);
    }

    void tick(IslandView island, RandomSource random) {
        xo = x;
        yo = y;
        zo = z;
        hopO = hop;
        entity.tickCount++;
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (walker && firstRest) {
            firstRest = false;
            rest(island, random, idle);
        }
        float moved = 0;
        if (slime && entity instanceof Slime s) {
            moved = slimeMove(island, random, s);
        } else {
            moved = walk(island, random);
        }

        float t = entity.tickCount + phase;
        boolean resting = moved == 0;
        float headYaw = body + (resting && entity instanceof Mob ? 40.0F * Mth.sin(t * 0.045F) : 0.0F);
        float pitch = resting && entity instanceof Mob ? 12.0F * Mth.sin(t * 0.031F) : 0.0F;
        if (ownTick) {
            try {
                tickOwn(living, headYaw, pitch);
                return;
            } catch (Throwable e) {
                ownTick = false; // its own tick doesn't work outside the world: animate it the simple way
            }
        }
        living.yBodyRotO = living.yBodyRot;
        living.yHeadRotO = living.yHeadRot;
        living.yRotO = living.getYRot();
        living.xRotO = living.getXRot();
        if (entity instanceof Slime s) {
            s.oSquish = s.squish;
            s.squish += (s.targetSquish - s.squish) * 0.5F;
            s.targetSquish *= 0.6F;
        }
        if (entity instanceof Mob) {
            living.yBodyRot = body;
            living.setYRot(body);
            living.yHeadRot = headYaw;
            living.setXRot(pitch);
            living.walkAnimation.update(Math.min(moved * 4.0F, 1.0F), 0.4F);
        }
    }

    /**
     * Moves the copy to its new spot (above the build limit) and runs its own tick, the way a client ticks a mob it
     * only sees: the mob's code turns the movement into its animation (walking, body turning, slime squish...).
     */
    private void tickOwn(LivingEntity living, float headYaw, float pitch) {
        living.setOldPosAndRot();
        Vec3 to = tickPos(x, y + hop, z);
        living.setDeltaMovement(to.subtract(living.position()));
        living.setPos(to);
        living.setOnGround(slime ? hopTick < 0 : grounded);
        if (living instanceof Mob) {
            living.setYRot(body);
            living.setXRot(pitch);
            living.yHeadRot = headYaw;
        }
        living.tick();
        if (inWater) {
            ((EntityAccessor) living).skyblocks$setWasTouchingWater(true);
        }
    }

    /** Walks towards the current target (turning first); returns how far it moved this tick. */
    private float walk(IslandView island, RandomSource random) {
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
        return moved;
    }

    /**
     * A slime hops towards its next spot: it jumps (an arc, moving forward), lands, waits a moment and jumps again;
     * once there it rests like the others. (Its squish comes from its own tick, which sees it leave and hit the
     * ground; the simple fallback sets the squish itself.) Returns how far it moved.
     */
    private float slimeMove(IslandView island, RandomSource random, Slime s) {
        float moved = 0;
        if (walker && hopTick >= 0) {
            hopTick++;
            hop = Math.sin(Math.PI * hopTick / HOP_TICKS) * HOP_HEIGHT;
            double dx = targetX - x, dz = targetZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.05) {
                double step = Math.min(HOP_SPEED, dist);
                double nx = x + dx / dist * step, nz = z + dz / dist * step;
                double floor = standable(island, nx, nz, y);
                if (!Double.isNaN(floor)) {
                    x = nx;
                    z = nz;
                    y = floor;
                    moved = (float) step;
                }
            }
            if (hopTick >= HOP_TICKS) {
                hopTick = -1;
                hop = 0;
                if (!ownTick) {
                    s.targetSquish = -0.5F; // landing squash
                }
                if (dist < 0.6) {
                    rest(island, random, 30 + random.nextInt(100)); // there: sit for a while
                } else {
                    idle = 6 + random.nextInt(18);
                }
            }
        } else if (walker && idle-- <= 0) {
            double dx = targetX - x, dz = targetZ - z;
            if (dx * dx + dz * dz < 0.25) {
                rest(island, random, 30 + random.nextInt(100));
            } else {
                body = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                if (!ownTick) {
                    s.targetSquish = 1.0F; // stretches as it jumps
                }
                hopTick = 0;
            }
        }
        return moved;
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
        double ry = Mth.lerp(partialTick, yo, y) + Mth.lerp(partialTick, hopO, hop);
        double rz = Mth.lerp(partialTick, zo, z);
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        int light = island.lightAt(rx, ry + 0.5, rz);
        PoseStack own = new PoseStack();
        own.last().pose().set(poseStack.last().pose());
        own.last().normal().set(poseStack.last().normal());
        // drawn from the island's block (for name tags and anything else that looks at where it is), not from where
        // it ticks; put back afterwards
        Vec3 ticking = entity.position();
        double oldX = entity.xo, oldY = entity.yo, oldZ = entity.zo, lastX = entity.xOld, lastY = entity.yOld, lastZ = entity.zOld;
        entity.setPos(display);
        entity.xo = entity.xOld = display.x;
        entity.yo = entity.yOld = display.y;
        entity.zo = entity.zOld = display.z;
        try {
            dispatcher.render(entity, rx, ry, rz, yaw, partialTick, own, buffer, light);
            return true;
        } catch (RuntimeException e) {
            return false; // a mob whose renderer needs it to be in the world
        } finally {
            entity.setPos(ticking);
            entity.xo = oldX;
            entity.yo = oldY;
            entity.zo = oldZ;
            entity.xOld = lastX;
            entity.yOld = lastY;
            entity.zOld = lastZ;
        }
    }
}
