package com.example.skyblocks.client.arcade;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Pac-Man: eat every dot in the maze with four ghosts after you (each chasing its own way, now and then going back to
 * their corners); a power pellet turns them blue for a while and you can eat them. Clear the maze for the next, faster.
 */
public class PacMan extends ArcadeGame {
    private static final String[] MAZE = {
            "#####################",
            "#o........#........o#",
            "#.###.###.#.###.###.#",
            "#...................#",
            "#.###.#.#####.#.###.#",
            "#.....#...#...#.....#",
            "#####.###.#.###.#####",
            "    #.#.......#.#    ",
            "#####.#.##.##.#.#####",
            "     ...#   #...     ",
            "#####.#.#####.#.#####",
            "#.........#.........#",
            "#.###.###.#.###.###.#",
            "#o..#.....P.....#..o#",
            "#####################"};
    private static final int MW = 21, MH = 15, CELL = 8, OX = (W - MW * CELL) / 2, OY = 20;
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[] GHOST_COLOURS = {6, 15, 12, 7};
    private static final int[][] CORNERS = {{19, 1}, {1, 1}, {19, 13}, {1, 13}};

    /** Something moving through the maze: a cell, a way it's going (DIRS index, -1 still) and how far to the next. */
    private static final class Mover {
        int x, y, dir = -1, startX, startY, wait;
        float prog;
        boolean eaten;

        float px() {
            return x + (dir >= 0 ? DIRS[dir][0] * prog : 0);
        }

        float py() {
            return y + (dir >= 0 ? DIRS[dir][1] * prog : 0);
        }
    }

    private final boolean[][] dots = new boolean[MH][MW], power = new boolean[MH][MW];
    private final Mover pac = new Mover();
    private final Mover[] ghosts = new Mover[4];
    private int want = -1, lives, level, frightened, eatenChain, dotsLeft, pause;

    @Override
    public String name() {
        return "PAC-MAN";
    }

    @Override
    public String[] help() {
        return new String[] {"Arrows / WASD: steer", "Eat the dots - pellets make ghosts edible"};
    }

    private static boolean wall(int x, int y) {
        if (y < 0 || y >= MH) return true;
        x = Math.floorMod(x, MW);
        return MAZE[y].charAt(x) == '#';
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        lives = 3;
        level = 0;
        fill();
        reset();
    }

    private void fill() {
        dotsLeft = 0;
        for (int y = 0; y < MH; y++) {
            for (int x = 0; x < MW; x++) {
                char c = MAZE[y].charAt(x);
                dots[y][x] = c == '.';
                power[y][x] = c == 'o';
                if (c == '.' || c == 'o') dotsLeft++;
            }
        }
    }

    private void reset() {
        pac.x = pac.startX = 10;
        pac.y = pac.startY = 13;
        pac.dir = -1;
        pac.prog = 0;
        want = -1;
        int[][] starts = {{10, 7}, {9, 9}, {10, 9}, {11, 9}};
        for (int i = 0; i < 4; i++) {
            Mover g = ghosts[i] == null ? (ghosts[i] = new Mover()) : ghosts[i];
            g.x = g.startX = starts[i][0];
            g.y = g.startY = starts[i][1];
            g.dir = -1;
            g.prog = 0;
            g.eaten = false;
            g.wait = i * 70; // (out of the house one by one)
        }
        frightened = 0;
        pause = 30;
    }

    private float pacSpeed() {
        return 0.16F + level * 0.01F;
    }

    private float ghostSpeed(Mover g) {
        return frightened > 0 ? 0.08F : 0.13F + level * 0.012F;
    }

    @Override
    public void tick() {
        ticks++;
        if (pause > 0) {
            pause--;
            return;
        }
        if (frightened > 0) frightened--;
        // pac
        if (pac.dir < 0 && want >= 0 && !wall(pac.x + DIRS[want][0], pac.y + DIRS[want][1])) {
            pac.dir = want;
        }
        if (pac.dir >= 0 && want >= 0 && want == (pac.dir + 2) % 4) { // (turning back: at once)
            pac.x += DIRS[pac.dir][0];
            pac.y += DIRS[pac.dir][1];
            pac.prog = 1 - pac.prog;
            pac.dir = want;
            wrap(pac);
        }
        if (pac.dir >= 0) {
            pac.prog += pacSpeed();
            if (pac.prog >= 1) {
                pac.prog -= 1;
                pac.x += DIRS[pac.dir][0];
                pac.y += DIRS[pac.dir][1];
                wrap(pac);
                eat(pac.x, pac.y);
                if (want >= 0 && !wall(pac.x + DIRS[want][0], pac.y + DIRS[want][1])) {
                    pac.dir = want;
                }
                if (wall(pac.x + DIRS[pac.dir][0], pac.y + DIRS[pac.dir][1])) {
                    pac.dir = -1;
                    pac.prog = 0;
                }
            }
        }
        // the ghosts
        boolean scatter = (ticks % 540) < 140;
        for (int i = 0; i < 4; i++) {
            Mover g = ghosts[i];
            if (g.wait > 0) {
                g.wait--;
                continue;
            }
            if (g.dir < 0) {
                g.dir = choose(g, i, scatter);
            }
            g.prog += ghostSpeed(g);
            if (g.prog >= 1) {
                g.prog -= 1;
                g.x += DIRS[g.dir][0];
                g.y += DIRS[g.dir][1];
                wrap(g);
                g.dir = choose(g, i, scatter);
            }
        }
        // meeting
        for (Mover g : ghosts) {
            if (g.wait > 0) continue;
            float dx = g.px() - pac.px(), dy = g.py() - pac.py();
            if (Math.abs(dx) > MW / 2.0F) dx = MW - Math.abs(dx);
            if (dx * dx + dy * dy < 0.5F) {
                if (frightened > 0 && !g.eaten) {
                    eatenChain++;
                    score += 100 << eatenChain;
                    g.eaten = true;
                    g.x = g.startX;
                    g.y = 9;
                    g.dir = -1;
                    g.prog = 0;
                    g.wait = 60;
                } else if (!(frightened > 0 && g.eaten && g.wait > 0)) {
                    if (--lives <= 0) {
                        over = true;
                    } else {
                        reset();
                    }
                    return;
                }
            }
        }
        if (dotsLeft == 0) { // (the maze cleared: the next one)
            level++;
            score += 1000;
            fill();
            reset();
        }
    }

    private static void wrap(Mover m) {
        m.x = Math.floorMod(m.x, MW);
    }

    private void eat(int x, int y) {
        if (dots[y][x]) {
            dots[y][x] = false;
            dotsLeft--;
            score += 10;
        } else if (power[y][x]) {
            power[y][x] = false;
            dotsLeft--;
            score += 50;
            frightened = Math.max(80, 180 - level * 20);
            eatenChain = 0;
            for (Mover g : ghosts) {
                g.eaten = false;
                if (g.dir >= 0 && g.wait == 0) g.dir = (g.dir + 2) % 4; // (they turn round)
            }
        }
    }

    /** A ghost at a cell: the way to go - never straight back unless it must, towards its target (or at random, blue). */
    private int choose(Mover g, int i, boolean scatter) {
        int tx, ty;
        if (scatter) {
            tx = CORNERS[i][0];
            ty = CORNERS[i][1];
        } else if (i == 0) { // straight for pac
            tx = pac.x;
            ty = pac.y;
        } else if (i == 1) { // ahead of it
            int d = pac.dir < 0 ? 0 : pac.dir;
            tx = pac.x + DIRS[d][0] * 4;
            ty = pac.y + DIRS[d][1] * 4;
        } else if (i == 2) { // round the far side of the red one
            tx = pac.x * 2 - ghosts[0].x;
            ty = pac.y * 2 - ghosts[0].y;
        } else { // close in, then shy off to its corner
            boolean near = Math.abs(g.x - pac.x) + Math.abs(g.y - pac.y) < 7;
            tx = near ? CORNERS[3][0] : pac.x;
            ty = near ? CORNERS[3][1] : pac.y;
        }
        int best = -1;
        double bestD = Double.MAX_VALUE;
        java.util.List<Integer> open = new java.util.ArrayList<>();
        for (int d = 0; d < 4; d++) {
            if (g.dir >= 0 && d == (g.dir + 2) % 4) continue;
            if (wall(g.x + DIRS[d][0], g.y + DIRS[d][1])) continue;
            if (g.y == 7 && g.x == 10 && d == 1 && g.wait == 0) continue; // (not back into the house)
            open.add(d);
            double dist = Math.pow(g.x + DIRS[d][0] - tx, 2) + Math.pow(g.y + DIRS[d][1] - ty, 2);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        if (open.isEmpty()) {
            return g.dir < 0 ? 0 : (g.dir + 2) % 4; // (a dead end: back)
        }
        if (frightened > 0 && !g.eaten) {
            return open.get(random.nextInt(open.size()));
        }
        return best;
    }

    @Override
    public void keyPressed(int key) {
        if (right(key)) want = 0;
        if (down(key)) want = 1;
        if (left(key)) want = 2;
        if (up(key)) want = 3;
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        for (int y = 0; y < MH; y++) {
            for (int x = 0; x < MW; x++) {
                int px = OX + x * CELL, py = OY + y * CELL;
                if (wall(x, y)) {
                    box(g, x0, y0, px, py, CELL, CELL, 0xFF1A2AA8);
                    box(g, x0, y0, px + 1, py + 1, CELL - 2, CELL - 2, 0xFF070B30);
                } else if (dots[y][x]) {
                    box(g, x0, y0, px + 3, py + 3, 2, 2, 0xFFFFD8B0);
                } else if (power[y][x] && (ticks / 8) % 2 == 0) {
                    box(g, x0, y0, px + 2, py + 2, 4, 4, 0xFFFFD8B0);
                }
            }
        }
        // pac: a yellow ball, its mouth opening and closing the way it goes
        float ppx = OX + pac.px() * CELL, ppy = OY + pac.py() * CELL;
        box(g, x0, y0, ppx + 1, ppy + 1, 6, 6, 0xFFFFE74A);
        box(g, x0, y0, ppx, ppy + 2, 8, 4, 0xFFFFE74A);
        box(g, x0, y0, ppx + 2, ppy, 4, 8, 0xFFFFE74A);
        if ((ticks / 3) % 2 == 0 && pac.dir >= 0) {
            int[] d = DIRS[pac.dir];
            box(g, x0, y0, ppx + 3 + d[0] * 2, ppy + 3 + d[1] * 2, 2 + Math.abs(d[0]) * 3, 2 + Math.abs(d[1]) * 3, 0xFF070B30);
        }
        for (int i = 0; i < 4; i++) {
            Mover m = ghosts[i];
            float gx = OX + m.px() * CELL, gy = OY + m.py() * CELL;
            boolean blue = frightened > 0 && !m.eaten;
            int c = blue ? (frightened < 40 && (ticks / 6) % 2 == 0 ? 0xFFFFFFFF : 0xFF2A4AFF) : rgb(GHOST_COLOURS[i]);
            box(g, x0, y0, gx + 1, gy, 6, 7, c);
            box(g, x0, y0, gx, gy + 2, 8, 6, c);
            box(g, x0, y0, gx + 1, gy + 2, 2, 2, 0xFFFFFFFF);
            box(g, x0, y0, gx + 5, gy + 2, 2, 2, 0xFFFFFFFF);
        }
        if (pause > 0 && ticks > 0) {
            centred(g, font, "READY!", x0 + W / 2, y0 + OY + 9 * CELL, 0xFFFFE74A);
        }
    }

    @Override
    public int sample(float x, float y) {
        float cx = (x - OX) / CELL, cy = (y - OY) / CELL;
        if (Math.abs(cx - pac.px() - 0.5F) < 0.7F && Math.abs(cy - pac.py() - 0.5F) < 0.7F) return 8;
        for (int i = 0; i < 4; i++) {
            Mover m = ghosts[i];
            if (Math.abs(cx - m.px() - 0.5F) < 0.7F && Math.abs(cy - m.py() - 0.5F) < 0.7F) {
                return frightened > 0 && !m.eaten ? 5 : GHOST_COLOURS[i];
            }
        }
        int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy);
        if (ix < 0 || iy < 0 || ix >= MW || iy >= MH) return 0;
        if (wall(ix, iy)) return 10;
        if (dots[iy][ix] || power[iy][ix]) return 4;
        return 0;
    }

    @Override
    public String status() {
        return "LIVES " + lives;
    }
}
