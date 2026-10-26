package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The shapes of the koi ponds, for shaders/core/koi_pond: a small texture, one row per pond shape. A pond of any shape
 * is a few (overlapping) rectangles, and the koi each keep to one of them - so they swim all through the pond, never
 * over the stone. Row: texel 0 = (how many rectangles, pond blocks low byte, high byte, -), then each rectangle
 * (west, east, north, south: blocks in from the west / north side of the rectangle round the whole pond).
 */
final class KoiPondData {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyBlocksMod.MOD_ID, "koi_pond_shapes");
    static final int MAX_RECTS = 8, ROWS = 256;

    private static DynamicTexture texture;
    private static final java.util.Map<String, Integer> ROWS_USED = new java.util.HashMap<>();
    private static boolean changed;

    private KoiPondData() {}

    /** Makes sure the texture is there (before the first pond is drawn). */
    static void ensure() {
        if (texture == null) {
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, 1 + MAX_RECTS, ROWS, true));
            Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            changed = true;
        }
        if (changed) {
            texture.upload();
            changed = false;
        }
    }

    /** The row for a pond's shape (its rectangles, as west, east, north, south in from the rectangle round it). */
    static int row(int[][] rects, int blocks) {
        StringBuilder key = new StringBuilder().append(blocks);
        for (int[] r : rects) {
            key.append(',').append(r[0]).append(' ').append(r[1]).append(' ').append(r[2]).append(' ').append(r[3]);
        }
        Integer known = ROWS_USED.get(key.toString());
        if (known != null) {
            return known;
        }
        ensure();
        if (ROWS_USED.size() >= ROWS) {
            ROWS_USED.clear(); // (a fresh start: every pond finds its row again within a second)
        }
        int row = ROWS_USED.size();
        ROWS_USED.put(key.toString(), row);
        NativeImage image = texture.getPixels();
        if (image != null) {
            image.setPixelRGBA(0, row, rgba(rects.length, blocks & 255, (blocks >> 8) & 255, 255));
            for (int i = 0; i < MAX_RECTS; i++) {
                int[] r = i < rects.length ? rects[i] : new int[4];
                image.setPixelRGBA(1 + i, row, rgba(r[0], r[1], r[2], r[3]));
            }
            changed = true;
        }
        return row;
    }

    private static int rgba(int r, int g, int b, int a) {
        return (r & 255) | (g & 255) << 8 | (b & 255) << 16 | (a & 255) << 24; // (NativeImage keeps them in that order)
    }

    /**
     * A pond's shape as a few rectangles (in its cells, in[z][x]): each time the rectangle that takes in the most
     * blocks not yet covered (the biggest first), until all are, or there are eight.
     */
    static int[][] cover(boolean[][] in) {
        int h = in.length, w = in[0].length;
        boolean[][] covered = new boolean[h][w];
        java.util.List<int[]> rects = new java.util.ArrayList<>();
        int[] run = new int[w];
        while (rects.size() < MAX_RECTS) {
            // how many blocks not yet covered in each top-left part (to count them in any rectangle at once)
            int[][] left = new int[h + 1][w + 1];
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    left[z + 1][x + 1] = left[z][x + 1] + left[z + 1][x] - left[z][x] + (in[z][x] && !covered[z][x] ? 1 : 0);
                }
            }
            if (left[h][w] == 0) {
                break;
            }
            int bestNew = 0, bestArea = 0;
            int[] best = null;
            java.util.Arrays.fill(run, 0);
            for (int z = 0; z < h; z++) {
                for (int x = 0; x < w; x++) {
                    run[x] = in[z][x] ? run[x] + 1 : 0;
                }
                for (int x0 = 0; x0 < w; x0++) {
                    int tall = Integer.MAX_VALUE;
                    for (int x1 = x0; x1 < w && run[x1] > 0; x1++) {
                        tall = Math.min(tall, run[x1]);
                        int z0 = z - tall + 1;
                        int fresh = left[z + 1][x1 + 1] - left[z0][x1 + 1] - left[z + 1][x0] + left[z0][x0];
                        int area = tall * (x1 - x0 + 1);
                        if (fresh > bestNew || (fresh == bestNew && fresh > 0 && area > bestArea)) {
                            bestNew = fresh;
                            bestArea = area;
                            best = new int[] {x0, x1, z0, z};
                        }
                    }
                }
            }
            if (best == null) {
                break;
            }
            rects.add(best);
            for (int z = best[2]; z <= best[3]; z++) {
                for (int x = best[0]; x <= best[1]; x++) {
                    covered[z][x] = true;
                }
            }
        }
        return rects.toArray(new int[0][]);
    }
}
