package com.example.skyblocks.island;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A mob living on a placed Sky Island (pedestal): its saved entity and where it is and is going, in its island's
 * block coordinates. The server moves it (the same wandering as the islands' mob figures always had: walk to a random
 * reachable spot, look around for a while, again; slimes hop) and decides when it has walked onto a neighbouring Sky
 * Island; clients run the same steps between the server's updates, so the figures they draw move smoothly.
 * Mobs that weren't standing on the ground when scanned (flying, swimming, pets told to sit) stay where they are.
 */
public final class IslandMob {
    public static final double SPEED = 0.06; // blocks per tick, in island blocks
    private static final int HOP_TICKS = 10;
    private static final double HOP_HEIGHT = 0.7, HOP_SPEED = 0.18;

    /** The islands around a mob, in its island's block coordinates. */
    public interface Space {
        /** Floor height a mob standing near (x, y, z) stands at, NaN if there is none (or no island there). */
        double floor(double x, double y, double z);

        /** Whether (x, z) is over an island (its own or a neighbouring Sky Island's). */
        boolean onIsland(double x, double z);
    }

    public final UUID id;
    public final CompoundTag entity;
    public final boolean walker, slime;
    /** Whether it is a mob (counts towards the island's "mobs" number, like a scan counts them). */
    public boolean isMob = true;
    /** Server: whether the clients watching its island already have its saved entity (updates then leave it out). */
    public transient boolean entitySent;
    public double x, y, z, xo, yo, zo;
    public double targetX, targetZ;
    public int idle;
    public float body;
    private boolean firstRest;
    public double hop, hopO;
    public int hopTick = -1;
    private long seed;
    /** How far it moved in the last tick (for the figure's walk animation). */
    public float moved;

    public IslandMob(UUID id, CompoundTag entity, boolean walker, boolean slime, double x, double y, double z, float body, long seed) {
        this.id = id;
        this.entity = entity;
        this.walker = walker;
        this.slime = slime;
        this.x = this.xo = this.targetX = x;
        this.y = this.yo = y;
        this.z = this.zo = this.targetZ = z;
        this.body = body;
        this.seed = seed;
        this.idle = 20 + nextInt(120);
        this.firstRest = true;
    }

    // ---- random numbers that can be sent to clients (SplitMix64) ----

    private long next() {
        long z = (seed += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private int nextInt(int bound) {
        return (int) Math.floorMod(next(), (long) bound);
    }

    private double nextDouble() {
        return (next() >>> 11) * 0x1.0p-53;
    }

    // ---- moving ----

    /** One tick; returns true if it made a random choice (a new spot to go to), which the server tells clients. */
    public boolean tick(Space space) {
        xo = x;
        yo = y;
        zo = z;
        hopO = hop;
        moved = 0;
        if (!walker) {
            return false;
        }
        boolean decided = false;
        if (firstRest) {
            firstRest = false;
            rest(space, idle);
            decided = true;
        }
        int before = decisions;
        moved = slime ? slimeMove(space) : walk(space);
        return decided || decisions != before;
    }

    private int decisions;

    private float walk(Space space) {
        float step = 0;
        if (idle <= 0) {
            double dx = targetX - x, dz = targetZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 0.25) {
                rest(space, 40 + nextInt(160)); // arrived: look around for a while
            } else {
                float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                body = Mth.approachDegrees(body, want, 15.0F);
                // turn first, walk once roughly facing the target
                if (Math.abs(Mth.degreesDifference(body, want)) < 35.0F) {
                    double s = Math.min(SPEED, dist);
                    double nx = x - Mth.sin(body * Mth.DEG_TO_RAD) * s;
                    double nz = z + Mth.cos(body * Mth.DEG_TO_RAD) * s;
                    double floor = standable(space, nx, nz, y);
                    if (Double.isNaN(floor)) {
                        rest(space, 10 + nextInt(20)); // blocked after all: pick another spot soon
                    } else {
                        x = nx;
                        z = nz;
                        y = floor;
                        step = (float) s;
                    }
                }
            }
        } else {
            idle--;
        }
        return step;
    }

    /** A slime hops towards its next spot: jumps (an arc, moving forward), lands, waits a moment, jumps again. */
    private float slimeMove(Space space) {
        float step = 0;
        if (hopTick >= 0) {
            hopTick++;
            hop = Math.sin(Math.PI * hopTick / HOP_TICKS) * HOP_HEIGHT;
            double dx = targetX - x, dz = targetZ - z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.05) {
                double s = Math.min(HOP_SPEED, dist);
                double nx = x + dx / dist * s, nz = z + dz / dist * s;
                double floor = standable(space, nx, nz, y);
                if (!Double.isNaN(floor)) {
                    x = nx;
                    z = nz;
                    y = floor;
                    step = (float) s;
                }
            }
            if (hopTick >= HOP_TICKS) {
                hopTick = -1;
                hop = 0;
                if (dist < 0.6) {
                    rest(space, 30 + nextInt(100)); // there: sit for a while
                } else {
                    idle = 6 + nextInt(18);
                    decisions++;
                }
            }
        } else if (idle-- <= 0) {
            double dx = targetX - x, dz = targetZ - z;
            if (dx * dx + dz * dz < 0.25) {
                rest(space, 30 + nextInt(100));
            } else {
                body = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                hopTick = 0;
            }
        }
        return step;
    }

    /**
     * Stops for the given time and picks the next spot to walk to: a random spot that can be reached in a straight line
     * without walking off an edge, into a wall or down a big drop (on its island or a neighbouring one). If none is
     * found it just stays and looks around.
     */
    private void rest(Space space, int ticks) {
        decisions++;
        idle = ticks;
        targetX = x;
        targetZ = z;
        for (int attempt = 0; attempt < 12; attempt++) {
            double tx = x + (nextDouble() - 0.5) * 12;
            double tz = z + (nextDouble() - 0.5) * 12;
            if (reachable(space, tx, tz)) {
                targetX = tx;
                targetZ = tz;
                return;
            }
        }
    }

    private boolean reachable(Space space, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0) {
            return false;
        }
        int steps = (int) Math.ceil(dist / 0.5);
        double height = y;
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            double floor = standable(space, x + dx * f, z + dz * f, height);
            if (Double.isNaN(floor)) {
                return false;
            }
            height = floor;
        }
        return true;
    }

    /** Floor height a mob at height y can step onto at (px, pz), or NaN (off the islands, wall, or big drop). */
    private static double standable(Space space, double px, double pz, double y) {
        // keep a little away from the outer edges: the spots around it must be over an island too
        if (!space.onIsland(px - 0.4, pz - 0.4) || !space.onIsland(px + 0.4, pz - 0.4)
                || !space.onIsland(px - 0.4, pz + 0.4) || !space.onIsland(px + 0.4, pz + 0.4)) {
            return Double.NaN;
        }
        double floor = space.floor(px, y, pz);
        if (Double.isNaN(floor) || floor > y + 1.05 || floor < y - 3.0) {
            return Double.NaN;
        }
        return floor;
    }

    // ---- converting to another island ----

    /** Moves it to another island's coordinates: offsets in island blocks (dx, dy, dz). */
    public void shift(double dx, double dy, double dz) {
        x += dx; xo += dx; targetX += dx;
        y += dy; yo += dy;
        z += dz; zo += dz; targetZ += dz;
    }

    // ---- saving / syncing ----

    public CompoundTag save(boolean withEntity) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        if (withEntity) {
            tag.put("entity", entity);
        }
        tag.putBoolean("walker", walker);
        tag.putBoolean("slime", slime);
        tag.putBoolean("is_mob", isMob);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putDouble("tx", targetX);
        tag.putDouble("tz", targetZ);
        tag.putInt("idle", idle);
        tag.putFloat("body", body);
        tag.putBoolean("first", firstRest);
        tag.putDouble("hop", hop);
        tag.putInt("hop_tick", hopTick);
        tag.putLong("seed", seed);
        return tag;
    }

    /** Loads a mob; {@code entity} is used when the tag has none (a sync that only updates where it is). */
    public static IslandMob load(CompoundTag tag, CompoundTag entity) {
        CompoundTag saved = tag.contains("entity") ? tag.getCompound("entity") : entity;
        IslandMob mob = new IslandMob(tag.getUUID("id"), saved == null ? new CompoundTag() : saved,
                tag.getBoolean("walker"), tag.getBoolean("slime"),
                tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("body"), tag.getLong("seed"));
        mob.isMob = !tag.contains("is_mob") || tag.getBoolean("is_mob");
        mob.targetX = tag.getDouble("tx");
        mob.targetZ = tag.getDouble("tz");
        mob.idle = tag.getInt("idle");
        mob.firstRest = tag.getBoolean("first");
        mob.hop = mob.hopO = tag.getDouble("hop");
        mob.hopTick = tag.getInt("hop_tick");
        return mob;
    }

    /** Takes over where another copy of this mob is (a server update), keeping this one's last position for smoothness. */
    public void follow(IslandMob server) {
        xo = x;
        yo = y;
        zo = z;
        hopO = hop;
        x = server.x;
        y = server.y;
        z = server.z;
        targetX = server.targetX;
        targetZ = server.targetZ;
        idle = server.idle;
        body = server.body;
        firstRest = server.firstRest;
        hop = server.hop;
        hopTick = server.hopTick;
        seed = server.seed;
    }
}
