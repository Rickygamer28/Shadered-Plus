package com.example.skyblocks.client.arcade;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Frogger: hop up across a road of cars and a river of logs to the four homes at the top. Fill all four for the next
 * level (everything faster). Hit by a car, in the water or carried off the side: a life gone.
 */
public class Frogger extends ArcadeGame {
    private static final int CELL = 16, COLS = W / CELL, TOP = 0;
    // rows from the top: 0 homes, 1-3 river, 4 bank, 5-7 road, 8 start (each 16 tall: 144)
    private static final int ROWS = 9, START = 8;
    private static final int[] HOMES = {1, 4, 7, 10};

    private static final class Lane {
        final int row, len, gap, colour;
        final float speed;
        float offset;

        Lane(int row, float speed, int len, int gap, int colour, float offset) {
            this.row = row;
            this.speed = speed;
            this.len = len;
            this.gap = gap;
            this.colour = colour;
            this.offset = offset;
        }

        /** Whether something of this lane is at x. */
        boolean at(float x) {
            float period = len + gap;
            float t = ((x - offset) % period + period) % period;
            return t < len;
        }
    }

    private Lane[] lanes;
    private final boolean[] home = new boolean[HOMES.length];
    private float frogX;
    private int frogRow, lives, level, best, dying, hopWait;

    @Override
    public String name() {
        return "FROGGER";
    }

    @Override
    public String[] help() {
        return new String[] {"Arrows / WASD: hop", "Over the road and river to the 4 homes"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        lives = 3;
        level = 0;
        java.util.Arrays.fill(home, false);
        makeLanes();
        resetFrog();
    }

    private void makeLanes() {
        float k = 1.0F + level * 0.25F;
        lanes = new Lane[] {
                new Lane(1, 0.7F * k, 56, 48, 7, 0), new Lane(2, -0.9F * k, 40, 44, 7, 30), new Lane(3, 0.6F * k, 72, 52, 7, 80),
                new Lane(5, -1.3F * k, 22, 58, 6, 10), new Lane(6, 1.0F * k, 30, 70, 8, 60), new Lane(7, -0.8F * k, 20, 46, 12, 0)};
    }

    private void resetFrog() {
        frogX = (COLS / 2) * CELL + CELL / 2.0F;
        frogRow = START;
        best = START;
    }

    private Lane lane(int row) {
        for (Lane l : lanes) {
            if (l.row == row) return l;
        }
        return null;
    }

    private void die() {
        dying = 24;
        if (--lives <= 0) {
            over = true;
        }
    }

    @Override
    public void tick() {
        ticks++;
        for (Lane l : lanes) {
            l.offset += l.speed;
        }
        if (hopWait > 0) hopWait--;
        if (dying > 0) {
            if (--dying == 0 && !over) resetFrog();
            return;
        }
        Lane l = lane(frogRow);
        if (frogRow >= 1 && frogRow <= 3 && l != null) {
            if (!l.at(frogX)) {
                die(); // (in the water)
                return;
            }
            frogX += l.speed; // (riding the log)
            if (frogX < 2 || frogX > W - 2) {
                die();
                return;
            }
        } else if (frogRow >= 5 && frogRow <= 7 && l != null) {
            if (l.at(frogX - 5) || l.at(frogX + 5) || l.at(frogX)) {
                die();
            }
        }
    }

    @Override
    public void keyPressed(int key) {
        if (dying > 0 || over || hopWait > 0) return;
        int dx = left(key) ? -1 : right(key) ? 1 : 0, dy = up(key) ? -1 : down(key) ? 1 : 0;
        if (dx == 0 && dy == 0) return;
        hopWait = 2;
        frogX = Mth.clamp(frogX + dx * CELL, CELL / 2.0F, W - CELL / 2.0F);
        frogRow = Mth.clamp(frogRow + dy, 0, START);
        if (frogRow < best) {
            best = frogRow;
            score += 10;
        }
        if (frogRow == 0) {
            int col = (int) (frogX / CELL);
            for (int i = 0; i < HOMES.length; i++) {
                if (HOMES[i] == col && !home[i]) {
                    home[i] = true;
                    score += 100;
                    boolean all = true;
                    for (boolean h : home) all &= h;
                    if (all) { // (all four: the next level)
                        score += 500;
                        level++;
                        java.util.Arrays.fill(home, false);
                        makeLanes();
                    }
                    resetFrog();
                    return;
                }
            }
            die(); // (into the hedge, or a home already taken)
        }
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        // the rows
        for (int r = 0; r < ROWS; r++) {
            int bg = r == 0 ? 0xFF1E5A2A : r <= 3 ? 0xFF1A3A8A : r == 4 ? 0xFF4A3A6A : r <= 7 ? 0xFF2A2A30 : 0xFF2E6A2E;
            box(g, x0, y0, 0, TOP + r * CELL, W, CELL, bg);
            if (r >= 5 && r <= 6) {
                for (int x = 4; x < W; x += 24) box(g, x0, y0, x, TOP + (r + 1) * CELL - 1, 12, 1, 0xFF8A8A8A);
            }
        }
        for (int i = 0; i < HOMES.length; i++) {
            int hx = HOMES[i] * CELL;
            box(g, x0, y0, hx + 1, TOP + 2, CELL - 2, CELL - 3, home[i] ? 0xFF3CC85A : 0xFF0E2A5A);
        }
        for (Lane l : lanes) {
            float period = l.len + l.gap;
            float start = ((l.offset % period) + period) % period - period;
            for (float x = start; x < W; x += period) {
                int y = TOP + l.row * CELL;
                if (l.row <= 3) {
                    box(g, x0, y0, Math.max(0, x), y + 3, Math.min(x + l.len, W) - Math.max(0, x), CELL - 6, 0xFF8A5A2A);
                    box(g, x0, y0, Math.max(0, x), y + 3, Math.min(x + l.len, W) - Math.max(0, x), 2, 0xFFA8783A);
                } else {
                    box(g, x0, y0, Math.max(0, x), y + 3, Math.min(x + l.len, W) - Math.max(0, x), CELL - 6, rgb(l.colour));
                    box(g, x0, y0, Math.max(0, x + 3), y + 5, Math.max(0, Math.min(x + l.len - 3, W) - Math.max(0, x + 3)), 3, 0x80000000);
                }
            }
        }
        // the frog (blinking red when it dies)
        int fy = TOP + frogRow * CELL;
        int col = dying > 0 ? ((dying / 3) % 2 == 0 ? 0xFFFF3A4A : 0xFF000000) : 0xFF5BFF6A;
        box(g, x0, y0, frogX - 5, fy + 3, 10, 10, col);
        box(g, x0, y0, frogX - 4, fy + 3, 2, 2, 0xFF000000);
        box(g, x0, y0, frogX + 2, fy + 3, 2, 2, 0xFF000000);
    }

    @Override
    public int sample(float x, float y) {
        int r = (int) ((y - TOP) / CELL);
        if (r < 0 || r >= ROWS) return 0;
        if (Math.abs(x - frogX) < 6 && r == frogRow) return dying > 0 ? 3 : 1;
        if (r == 0) {
            int c = (int) (x / CELL);
            for (int i = 0; i < HOMES.length; i++) {
                if (HOMES[i] == c) return home[i] ? 1 : 14;
            }
            return 9;
        }
        Lane l = lane(r);
        if (l != null && l.at(x)) return r <= 3 ? 7 : l.colour;
        return r <= 3 ? 10 : r == 4 ? 11 : r <= 7 ? 13 : 9;
    }

    @Override
    public String status() {
        return "LIVES " + lives + "  LV " + (level + 1);
    }
}
