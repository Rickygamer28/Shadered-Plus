package com.example.zinogre;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lights an arena evenly with hidden light blocks. It works out how the block light spreads through the cavern (from
 * the lanterns, candles and lights already there, the same way the game spreads it: one level less per block, blocked
 * by solid blocks), then adds a light wherever a surface you can see (any air next to a block: floor, walls, roof,
 * stakes, the pen) is still darker than the target, two blocks out from that surface, until none is.
 */
final class ArenaLighting {
    /** The space around the core that's looked at: the whole cavern with its pen and tunnels. */
    static final int RADIUS = 37, BELOW = 2, ABOVE = 36;
    private static final int SX = RADIUS * 2 + 1, SY = BELOW + ABOVE + 1, SZ = SX;
    private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Whether every chunk the arena covers is loaded (it's only lit in one go). */
    static boolean loaded(ServerLevel level, BlockPos core) {
        for (int cx = SectionPos.blockToSectionCoord(core.getX() - RADIUS); cx <= SectionPos.blockToSectionCoord(core.getX() + RADIUS); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(core.getZ() - RADIUS); cz <= SectionPos.blockToSectionCoord(core.getZ() + RADIUS); cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Lights the arena around this core so its surfaces are at least {@code target} bright; returns {added, darkest}. */
    static int[] light(ServerLevel level, BlockPos core, int target) {
        int n = SX * SY * SZ;
        byte[] cost = new byte[n];   // how much light a block takes away (1 for air .. 15 for solid)
        boolean[] air = new boolean[n];
        byte[] light = new byte[n];
        int[] queue = new int[n * 2];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int ox = core.getX() - RADIUS, oy = core.getY() - BELOW, oz = core.getZ() - RADIUS;
        int qh = 0, qt = 0;
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                boolean inside = (x - RADIUS) * (x - RADIUS) + (z - RADIUS) * (z - RADIUS) <= RADIUS * RADIUS;
                for (int y = 0; y < SY; y++) {
                    int i = index(x, y, z);
                    if (!inside) {
                        cost[i] = 15;
                        continue;
                    }
                    at.set(ox + x, oy + y, oz + z);
                    BlockState state = level.getBlockState(at);
                    air[i] = state.isAir();
                    cost[i] = (byte) Math.max(1, Math.min(15, state.getLightBlock(level, at)));
                    int emit = state.getLightEmission(level, at);
                    if (emit > 0) {
                        light[i] = (byte) emit;
                        queue[qt++] = i;
                    }
                }
            }
        }
        // the cavern: the air reachable from just above the middle of the pit floor
        boolean[] cavern = new boolean[n];
        int[] flood = new int[n];
        int fh = 0, ft = 0;
        for (int y = BELOW + 2; y < BELOW + 6; y++) {
            int s = index(RADIUS, y, RADIUS);
            if (air[s] && !cavern[s]) {
                cavern[s] = true;
                flood[ft++] = s;
            }
        }
        while (fh < ft) {
            int i = flood[fh++];
            int x = i / (SY * SZ), y = i / SZ % SY, z = i % SZ;
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= SX || ny >= SY || nz >= SZ) continue;
                int j = index(nx, ny, nz);
                // through air and anything see-through (fences, chains, bars, the pen gate)
                if (!cavern[j] && cost[j] < 15) {
                    cavern[j] = true;
                    flood[ft++] = j;
                }
            }
        }
        spread(light, cost, queue, qh, qt);

        BlockState lightBlock = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        int added = 0;
        for (int pass = 0; pass < 2; pass++) {
            // lowest first: the floor and the ledge get their lights first, and they reach up the walls too
            for (int y = 0; y < SY; y++) {
                for (int x = 0; x < SX; x++) {
                    for (int z = 0; z < SZ; z++) {
                        int i = index(x, y, z);
                        if (!cavern[i] || !air[i] || light[i] >= target || !surface(cost, x, y, z)) continue;
                        int p = awayFromSurface(cost, air, cavern, x, y, z);
                        if (light[p] >= 15) continue;
                        light[p] = 15;
                        air[p] = false; // taken now
                        queue[0] = p;
                        spread(light, cost, queue, 0, 1);
                        at.set(ox + p / (SY * SZ), oy + p / SZ % SY, oz + p % SZ);
                        level.setBlock(at, lightBlock, Block.UPDATE_CLIENTS);
                        added++;
                    }
                }
            }
        }
        int darkest = 15;
        for (int i = 0; i < n; i++) {
            if (cavern[i] && air[i] && surface(cost, i / (SY * SZ), i / SZ % SY, i % SZ)) {
                darkest = Math.min(darkest, light[i]);
            }
        }
        return new int[] {added, darkest};
    }

    private static int index(int x, int y, int z) {
        return (x * SY + y) * SZ + z;
    }

    /** Air next to something solid: a surface the player sees lit by this block's light. */
    private static boolean surface(byte[] cost, int x, int y, int z) {
        for (int[] d : DIRS) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= SX || ny >= SY || nz >= SZ) continue;
            if (cost[index(nx, ny, nz)] >= 15) return true;
        }
        return false;
    }

    /** Where to put the light for a dark surface: up to two blocks out from it into the open, so it's an even glow. */
    private static int awayFromSurface(byte[] cost, boolean[] air, boolean[] cavern, int x, int y, int z) {
        int bx = 0, by = 0, bz = 0;
        for (int[] d : DIRS) {
            int nx = x + d[0], ny = y + d[1], nz = z + d[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= SX || ny >= SY || nz >= SZ || cost[index(nx, ny, nz)] >= 15) {
                bx -= d[0];
                by -= d[1];
                bz -= d[2];
            }
        }
        int[] step = Math.abs(by) >= Math.abs(bx) && Math.abs(by) >= Math.abs(bz) && by != 0 ? new int[] {0, Integer.signum(by), 0}
                : Math.abs(bx) >= Math.abs(bz) && bx != 0 ? new int[] {Integer.signum(bx), 0, 0}
                : bz != 0 ? new int[] {0, 0, Integer.signum(bz)} : new int[] {0, 0, 0};
        int best = index(x, y, z);
        for (int k = 1; k <= 2; k++) {
            int nx = x + step[0] * k, ny = y + step[1] * k, nz = z + step[2] * k;
            if (nx < 0 || ny < 0 || nz < 0 || nx >= SX || ny >= SY || nz >= SZ) break;
            int j = index(nx, ny, nz);
            if (!air[j] || !cavern[j]) break;
            best = j;
        }
        return best;
    }

    /** Spreads the light out from the queued blocks, like the game: one level less per block (more through glass-like blocks). */
    private static void spread(byte[] light, byte[] cost, int[] queue, int head, int tail) {
        while (head < tail) {
            int i = queue[head++];
            int level = light[i];
            int x = i / (SY * SZ), y = i / SZ % SY, z = i % SZ;
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= SX || ny >= SY || nz >= SZ) continue;
                int j = index(nx, ny, nz);
                int v = level - cost[j];
                if (v > light[j]) {
                    light[j] = (byte) v;
                    if (tail == queue.length) { // compact the queue
                        System.arraycopy(queue, head, queue, 0, tail - head);
                        tail -= head;
                        head = 0;
                    }
                    queue[tail++] = j;
                }
            }
        }
    }

    private ArenaLighting() {}
}
