package com.example.skyblocks.client.arcade;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Whisker Maze: a hungry cat eating every treat in the maze, four slimes after it, each with the classic maze-chase
 * brains: Ember heads straight for the cat (and, when few treats are left, speeds up and stops going back to its corner),
 * Bloom aims four squares ahead of it (and, as in the old machines, four up and four left when it faces up), Frost aims
 * at the point mirroring Ember through two squares ahead of it, Moss closes in until it's within eight squares and then
 * shies off to its corner. They take turns scattering to their corners and chasing on a set timetable (frozen while
 * they're scared), all turn round when it changes, choose at each crossing the way nearest their target (never straight
 * back; ties up, left, down, right), won't turn up at the crossings just above their house and the cat's start, and slow
 * down in the side tunnel. They leave their house by treat counts, or when the cat stops eating for a while. A ball of
 * yarn makes them pale and wander at random for a time that shrinks with each maze (later, not at all); they flee home
 * as eyes when caught. Speeds and timings rise with the mazes as on the old machines. A fish now and then for bonus
 * points; a life more at 10000.
 */
public class WhiskerMaze extends ArcadeGame {
    private static final String[] MAZE = {
            "###########################",
            "#o.....#...........#.....o#",
            "#.###..#.####.####.#..###.#",
            "#.#.....................#.#",
            "#.#.##.###.##.##.###.##.#.#",
            "#......#...........#......#",
            "####.#.#.###.#.###.#.#.####",
            "   #.#...............#.#   ",
            "####.#.#.####-####.#.#.####",
            "    ...#.#       #.#...    ",
            "####.#.#.#########.#.#.####",
            "   #.#...............#.#   ",
            "####.#.####.#.#.####.#.####",
            "#.........#.....#.........#",
            "#.##.####.#.#.#.#.####.##.#",
            "#o.#........#.#........#.o#",
            "##.#.#.####.#.#.####.#.#.##",
            "#....#...............#....#",
            "#.########.##.##.########.#",
            "#.........................#",
            "###########################"};
    private static final int MW = 27, MH = 21, CELL = 6, OX = (W - MW * CELL) / 2, OY = 14;
    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[] COLOURS = {6, 15, 12, 9};
    private static final int[] TINTS = {0xFFFF5A3A, 0xFFFF7AD9, 0xFF3AE8FF, 0xFF6AE85A};
    private static final int[][] CORNERS = {{25, -2}, {1, -2}, {25, 22}, {1, 22}};
    private static final int DOOR_X = 13, DOOR_Y = 8, OUT_Y = 7, HOME_Y = 9;
    /** Scatter, chase, scatter, chase ... in ticks (then chasing for good): maze 1, mazes 2-4, mazes 5 on. */
    private static final int[][] SCHEDULES = {{140, 400, 140, 400, 100, 400, 100}, {140, 400, 140, 400, 100, 20660, 1},
            {100, 400, 100, 400, 100, 20740, 1}};
    /** How long the yarn scares them (ticks), maze by maze - shorter and shorter, then not at all. */
    private static final int[] SCARED = {120, 100, 80, 60, 40, 100, 40, 40, 20, 100, 40, 20, 20, 60, 20, 20, 0, 20, 0};
    /** How few treats left makes Ember hurry, maze by maze. */
    private static final int[] HURRY = {20, 30, 40, 40, 40, 50, 50, 50, 60, 60, 60, 80, 80, 80, 100, 100, 100, 100, 120};
    /** Where they may not turn up (the crossings above their house and above the cat's start). */
    private static final int[][] NO_UP = {{12, 7}, {14, 7}, {11, 13}, {15, 13}};
    /** Top speed (squares a tick), which the others are shares of. */
    private static final float TOP = 0.25F;

    private enum State { HOUSE, LEAVING, ACTIVE, EYES }

    private static final class Mover {
        int x, y, dir = -1, startX, startY, release;
        /** Scared by the last ball of yarn (not one caught and come back since). */
        boolean fright;
        float prog;
        State state = State.ACTIVE;

        float px() {
            return x + (dir >= 0 ? DIRS[dir][0] * prog : 0);
        }

        float py() {
            return y + (dir >= 0 ? DIRS[dir][1] * prog : 0);
        }
    }

    private final boolean[][] treats = new boolean[MH][MW], yarn = new boolean[MH][MW];
    private final Mover cat = new Mover();
    private final Mover[] slimes = new Mover[4];
    private int want = -1, lives, level, scared, chain, left, eaten, pause, dying, clearing, phaseTick, fish, fishShown, sinceTreat;
    private boolean extraGiven;
    private final List<int[]> popups = new ArrayList<>(); // (x, y, points, ticks left)

    @Override
    public String name() {
        return "WHISKER MAZE";
    }

    @Override
    public String[] help() {
        return new String[] {"Arrows / WASD: steer the cat", "Yarn makes the slimes catchable"};
    }

    private static boolean wall(int x, int y, boolean slime) {
        if (y < 0 || y >= MH) return true;
        char c = MAZE[y].charAt(Math.floorMod(x, MW));
        return c == '#' || (c == '-' && !slime);
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        lives = 3;
        level = 0;
        extraGiven = false;
        newMaze();
    }

    private void newMaze() {
        left = 0;
        eaten = 0;
        fishShown = 0;
        fish = 0;
        for (int y = 0; y < MH; y++) {
            for (int x = 0; x < MW; x++) {
                char c = MAZE[y].charAt(x);
                treats[y][x] = c == '.' && !(y == 15 && x == 13);
                yarn[y][x] = c == 'o';
                if (treats[y][x] || yarn[y][x]) left++;
            }
        }
        reset();
    }

    private void reset() {
        cat.x = 13;
        cat.y = 15;
        cat.dir = -1;
        cat.prog = 0;
        want = -1;
        int[][] starts = {{13, OUT_Y}, {13, HOME_Y}, {11, HOME_Y}, {15, HOME_Y}};
        int[] releases = level == 0 ? new int[] {0, 0, 30, 60} : level == 1 ? new int[] {0, 0, 0, 50} : new int[] {0, 0, 0, 0};
        for (int i = 0; i < 4; i++) {
            Mover s = slimes[i] == null ? (slimes[i] = new Mover()) : slimes[i];
            s.x = s.startX = starts[i][0];
            s.y = s.startY = starts[i][1];
            s.dir = i == 0 ? 2 : -1;
            s.prog = 0;
            s.state = i == 0 ? State.ACTIVE : State.HOUSE;
            s.release = releases[i];
        }
        scared = 0;
        phaseTick = 0;
        sinceTreat = 0;
        pause = 50;
        popups.clear();
    }

    private boolean scatter() {
        int[] schedule = SCHEDULES[level == 0 ? 0 : level < 4 ? 1 : 2];
        int t = phaseTick;
        for (int i = 0; i < schedule.length; i++) {
            if (t < schedule[i]) return i % 2 == 0;
            t -= schedule[i];
        }
        return false;
    }

    /** Which of the three speed tables this maze uses (maze 1; 2-4; 5 on). */
    private int band() {
        return level == 0 ? 0 : level < 4 ? 1 : 2;
    }

    private float catSpeed() {
        float[] normal = {0.8F, 0.9F, 1.0F}, eating = {0.9F, 0.95F, 1.0F};
        return TOP * (scared > 0 ? eating : normal)[band()];
    }

    /** 0: not hurrying; 1, 2: Ember hurrying (fewer treats left, faster). */
    private int hurry() {
        int h = HURRY[Math.min(level, HURRY.length - 1)];
        return left <= h / 2 ? 2 : left <= h ? 1 : 0;
    }

    private float slimeSpeed(int i, Mover s) {
        if (s.state == State.EYES) return 0.4F;
        if (s.state == State.HOUSE || s.state == State.LEAVING) return 0.1F;
        int b = band();
        if (s.y == 9 && (s.x < 5 || s.x > 21)) return TOP * new float[] {0.4F, 0.45F, 0.5F}[b]; // (slow in the tunnel)
        if (scared > 0 && s.fright) return TOP * new float[] {0.5F, 0.55F, 0.6F}[b];
        float v = TOP * new float[] {0.75F, 0.85F, 0.95F}[b];
        if (i == 0) v += TOP * 0.05F * hurry(); // (Ember hurries when few treats are left)
        return v;
    }

    @Override
    public void tick() {
        ticks++;
        popups.removeIf(p -> --p[3] <= 0);
        if (pause > 0) {
            pause--;
            return;
        }
        if (dying > 0) {
            if (--dying == 0) {
                if (--lives <= 0) {
                    over = true;
                } else {
                    reset();
                }
            }
            return;
        }
        if (clearing > 0) {
            if (--clearing == 0) {
                level++;
                newMaze();
            }
            return;
        }
        boolean wasScatter = scatter();
        if (scared > 0) {
            scared--;
        } else {
            phaseTick++;
        }
        // nobody eating for a while: the next one out of the house comes out anyway
        if (++sinceTreat >= (level < 4 ? 80 : 60)) {
            sinceTreat = 0;
            for (int k = 1; k < 4; k++) {
                if (slimes[k].state == State.HOUSE) {
                    slimes[k].release = 0;
                    break;
                }
            }
        }
        if (scatter() != wasScatter) { // (the mode changes: they all turn round)
            for (Mover s : slimes) {
                if (s.state == State.ACTIVE && s.dir >= 0) s.dir = (s.dir + 2) % 4;
            }
        }
        if (fish > 0) fish--;
        moveCat();
        for (int i = 0; i < 4; i++) {
            moveSlime(i);
        }
        meet();
        if (left == 0) {
            clearing = 60;
            score += 500;
        }
        if (!extraGiven && score >= 10000) {
            extraGiven = true;
            lives++;
        }
    }

    private int lastDir = 2;

    private void moveCat() {
        if (cat.dir >= 0) lastDir = cat.dir;
        if (cat.dir < 0 && want >= 0 && !wall(cat.x + DIRS[want][0], cat.y + DIRS[want][1], false)) {
            cat.dir = want;
        }
        if (cat.dir >= 0 && want >= 0 && want == (cat.dir + 2) % 4) { // (turning back: at once)
            cat.x += DIRS[cat.dir][0];
            cat.y += DIRS[cat.dir][1];
            cat.x = Math.floorMod(cat.x, MW);
            cat.prog = 1 - cat.prog;
            cat.dir = want;
        }
        if (cat.dir < 0) return;
        cat.prog += catSpeed();
        if (cat.prog >= 1) {
            cat.prog -= 1;
            cat.x = Math.floorMod(cat.x + DIRS[cat.dir][0], MW);
            cat.y += DIRS[cat.dir][1];
            eat(cat.x, cat.y);
            if (want >= 0 && !wall(cat.x + DIRS[want][0], cat.y + DIRS[want][1], false)) {
                cat.dir = want;
            }
            if (wall(cat.x + DIRS[cat.dir][0], cat.y + DIRS[cat.dir][1], false)) {
                cat.dir = -1;
                cat.prog = 0;
            }
        }
    }

    private void eat(int x, int y) {
        if (treats[y][x]) {
            treats[y][x] = false;
            score += 10;
            gone();
        } else if (yarn[y][x]) {
            yarn[y][x] = false;
            score += 50;
            gone();
            scared = SCARED[Math.min(level, SCARED.length - 1)]; // (later mazes: they only turn round)
            chain = 0;
            for (Mover s : slimes) {
                s.fright = s.state != State.EYES;
            }
            for (Mover s : slimes) {
                if (s.state == State.ACTIVE && s.dir >= 0) s.dir = (s.dir + 2) % 4;
            }
        }
        if (fish > 0 && x == 13 && y == 13) { // (the fish)
            int[] values = {100, 300, 500, 700, 1000, 2000, 3000, 5000};
            int v = values[Math.min(level, values.length - 1)];
            score += v;
            popups.add(new int[] {13, 13, v, 40});
            fish = 0;
        }
    }

    private void gone() {
        left--;
        eaten++;
        if ((eaten == 70 || eaten == 170) && fishShown < 2) {
            fishShown++;
            fish = 180;
        }
        sinceTreat = 0;
        for (int k = 1; k < 4; k++) { // (the next one due out of the house counts the treats eaten)
            Mover s = slimes[k];
            if (s.state == State.HOUSE) {
                if (s.release > 0) s.release--;
                break;
            }
        }
    }

    private void moveSlime(int i) {
        Mover s = slimes[i];
        if (s.state == State.HOUSE) {
            if (s.release <= 0) {
                s.state = State.LEAVING;
                s.dir = -1;
            } else {
                return;
            }
        }
        if (s.dir < 0) s.dir = choose(s, i);
        if (s.dir < 0) return;
        s.prog += slimeSpeed(i, s);
        if (s.prog >= 1) {
            s.prog -= 1;
            s.x = Math.floorMod(s.x + DIRS[s.dir][0], MW);
            s.y += DIRS[s.dir][1];
            if (s.state == State.LEAVING && s.x == DOOR_X && s.y == OUT_Y) {
                s.state = State.ACTIVE;
            } else if (s.state == State.EYES && s.x == DOOR_X && s.y == HOME_Y) {
                s.state = State.LEAVING; // (home: whole again, out it comes)
            }
            s.dir = choose(s, i);
        }
    }

    /** At a cell: the way to go - not straight back unless it must, towards its target (at random when scared). */
    private int choose(Mover s, int i) {
        int tx, ty;
        boolean door = s.state != State.ACTIVE;
        if (s.state == State.LEAVING) {
            tx = DOOR_X;
            ty = s.x == DOOR_X ? OUT_Y : HOME_Y;
        } else if (s.state == State.EYES) {
            tx = DOOR_X;
            ty = s.y >= OUT_Y && s.x == DOOR_X ? HOME_Y : OUT_Y;
        } else if (scatter() && !(i == 0 && hurry() > 0 && slimes[3].state != State.HOUSE)) {
            tx = CORNERS[i][0];
            ty = CORNERS[i][1];
        } else if (i == 0) {
            tx = cat.x;
            ty = cat.y;
        } else if (i == 1) {
            int d = cat.dir < 0 ? lastDir : cat.dir;
            tx = cat.x + DIRS[d][0] * 4 - (d == 3 ? 4 : 0); // (facing up: up and left, as the old machines did)
            ty = cat.y + DIRS[d][1] * 4;
        } else if (i == 2) {
            int d = cat.dir < 0 ? lastDir : cat.dir;
            int ax = cat.x + DIRS[d][0] * 2 - (d == 3 ? 2 : 0), ay = cat.y + DIRS[d][1] * 2;
            tx = ax * 2 - slimes[0].x;
            ty = ay * 2 - slimes[0].y;
        } else {
            boolean near = Math.pow(s.x - cat.x, 2) + Math.pow(s.y - cat.y, 2) < 64;
            tx = near ? CORNERS[3][0] : cat.x;
            ty = near ? CORNERS[3][1] : cat.y;
        }
        int best = -1;
        double bestD = Double.MAX_VALUE;
        List<Integer> open = new ArrayList<>();
        for (int d : new int[] {3, 2, 1, 0}) { // (ties: up, left, down, right - as in the old machines)
            if (s.dir >= 0 && d == (s.dir + 2) % 4) continue;
            int nx = s.x + DIRS[d][0], ny = s.y + DIRS[d][1];
            if (wall(nx, ny, door)) continue;
            if (s.state == State.ACTIVE && MAZE[ny].charAt(Math.floorMod(nx, MW)) == '-') continue;
            if (d == 3 && s.state == State.ACTIVE && !(scared > 0 && s.fright) && noUp(s.x, s.y)) continue;
            open.add(d);
            double dist = Math.pow(nx - tx, 2) + Math.pow(ny - ty, 2);
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        if (open.isEmpty()) {
            int back = s.dir < 0 ? 3 : (s.dir + 2) % 4;
            return wall(s.x + DIRS[back][0], s.y + DIRS[back][1], door) ? -1 : back;
        }
        if (scared > 0 && s.fright && s.state == State.ACTIVE) {
            return open.get(random.nextInt(open.size()));
        }
        return best;
    }

    private static boolean noUp(int x, int y) {
        for (int[] c : NO_UP) {
            if (c[0] == x && c[1] == y) return true;
        }
        return false;
    }

    private void meet() {
        for (Mover s : slimes) {
            if (s.state != State.ACTIVE) continue;
            float dx = s.px() - cat.px(), dy = s.py() - cat.py();
            if (Math.abs(dx) > MW / 2.0F) dx = MW - Math.abs(dx);
            if (dx * dx + dy * dy < 0.45F) {
                if (scared > 0 && s.fright) {
                    s.fright = false;
                    chain++;
                    int v = 100 << chain;
                    score += v;
                    popups.add(new int[] {s.x, s.y, v, 40});
                    s.state = State.EYES;
                    pause = 12; // (a beat for the catch)
                } else {
                    dying = 50;
                    return;
                }
            }
        }
    }

    @Override
    public void keyPressed(int key) {
        if (right(key)) want = 0;
        if (down(key)) want = 1;
        if (left(key)) want = 2;
        if (up(key)) want = 3;
    }

    // ================= drawing =================

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        boolean flash = clearing > 0 && (clearing / 8) % 2 == 0;
        int edge = flash ? 0xFFFFFFFF : 0xFFB45BFF, fillC = 0xFF180A30;
        for (int y = 0; y < MH; y++) {
            for (int x = 0; x < MW; x++) {
                int px = OX + x * CELL, py = OY + y * CELL;
                char c = MAZE[y].charAt(x);
                if (c == '#') {
                    box(g, x0, y0, px, py, CELL, CELL, fillC);
                    // (a neon line along each side that faces the corridors)
                    if (!wall(x, y - 1, true) && y > 0) box(g, x0, y0, px, py, CELL, 1, edge);
                    if (!wall(x, y + 1, true) && y < MH - 1) box(g, x0, y0, px, py + CELL - 1, CELL, 1, edge);
                    if (x > 0 && !wall(x - 1, y, true)) box(g, x0, y0, px, py, 1, CELL, edge);
                    if (x < MW - 1 && !wall(x + 1, y, true)) box(g, x0, y0, px + CELL - 1, py, 1, CELL, edge);
                } else if (c == '-') {
                    box(g, x0, y0, px, py + 2, CELL, 2, 0xFFFF7AD9);
                } else if (treats[y][x]) {
                    box(g, x0, y0, px + 2, py + 2, 2, 2, 0xFFFFE2B0);
                } else if (yarn[y][x]) {
                    if ((ticks / 8) % 2 == 0 || pause > 0) {
                        box(g, x0, y0, px + 1, py + 1, 4, 4, 0xFFFF7AD9);
                        box(g, x0, y0, px + 2, py + 1, 1, 4, 0xFFFFB0E8);
                    }
                }
            }
        }
        if (fish > 0) { // (a little orange fish)
            int fx = OX + 13 * CELL, fy = OY + 13 * CELL;
            box(g, x0, y0, fx, fy + 1, 4, 4, 0xFFFF9A2E);
            box(g, x0, y0, fx + 4, fy, 2, 6, 0xFFFF9A2E);
            box(g, x0, y0, fx + 1, fy + 2, 1, 1, 0xFF000000);
        }
        for (int i = 0; i < 4; i++) {
            drawSlime(g, x0, y0, i, pt);
        }
        if (clearing == 0) drawCat(g, x0, y0);
        for (int[] p : popups) {
            g.pose().pushPose();
            g.pose().translate(x0 + OX + p[0] * CELL, y0 + OY + p[1] * CELL - 2, 0);
            g.pose().scale(0.6F, 0.6F, 1.0F);
            g.drawString(font, Integer.toString(p[2]), 0, 0, 0xFF3AE8FF, false);
            g.pose().popPose();
        }
        if (pause > 20 && ticks < 5 || (pause > 0 && dying == 0 && clearing == 0 && pause > 12)) {
            centred(g, font, "READY!", x0 + W / 2, y0 + OY + 9 * CELL - 1, 0xFFFFE74A);
        }
    }

    private void drawCat(GuiGraphics g, int x0, int y0) {
        float cx = OX + cat.px() * CELL - 0.5F, cy = OY + cat.py() * CELL - 0.5F;
        if (dying > 0) { // (it curls up, smaller and smaller)
            float k = dying / 50.0F;
            int s = Math.max(1, Math.round(7 * k));
            box(g, x0, y0, cx + (7 - s) / 2.0F, cy + (7 - s) / 2.0F, s, s, 0xFFB8B8C8);
            return;
        }
        int fur = 0xFFB8B8C8, dark = 0xFF5A5A6A;
        box(g, x0, y0, cx, cy + 2, 7, 5, fur);           // head
        box(g, x0, y0, cx, cy, 2, 2, fur);               // ears
        box(g, x0, y0, cx + 5, cy, 2, 2, fur);
        box(g, x0, y0, cx + 3, cy + 2, 1, 1, dark);     // a stripe
        box(g, x0, y0, cx + 1, cy + 3, 1, 1, 0xFF3AE860); // eyes
        box(g, x0, y0, cx + 5, cy + 3, 1, 1, 0xFF3AE860);
        boolean open = (ticks / 3) % 2 == 0 && cat.dir >= 0;
        box(g, x0, y0, cx + 2, cy + 5, 3, open ? 2 : 1, open ? 0xFFFF7AD9 : dark); // mouth, munching
    }

    private void drawSlime(GuiGraphics g, int x0, int y0, int i, float pt) {
        Mover s = slimes[i];
        float sx = OX + s.px() * CELL - 0.5F, sy = OY + s.py() * CELL - 0.5F;
        if (s.state == State.HOUSE) sy += (ticks / 6) % 2; // (bobbing, waiting)
        int look = s.dir < 0 ? 1 : s.dir;
        int ex = DIRS[look][0], ey = DIRS[look][1];
        if (s.state != State.EYES) {
            boolean pale = scared > 0 && s.fright;
            int c = pale ? (scared < 50 && (ticks / 5) % 2 == 0 ? 0xFFFFFFFF : 0xFF5A7AFF) : TINTS[i];
            box(g, x0, y0, sx + 1, sy, 5, 1, c);           // a round top
            box(g, x0, y0, sx, sy + 1, 7, 5, c);
            int wob = (ticks / 4) % 2;                    // a wobbling foot
            box(g, x0, y0, sx + wob, sy + 6, 2, 1, c);
            box(g, x0, y0, sx + 4 + wob, sy + 6, 2, 1, c);
            box(g, x0, y0, sx + 1, sy + 1, 1, 2, 0x60FFFFFF); // a shine
            if (pale) {
                box(g, x0, y0, sx + 1, sy + 4, 5, 1, 0xFFFFC0C0); // a wobbly worried mouth
                return;
            }
        }
        box(g, x0, y0, sx + 1, sy + 2, 2, 2, 0xFFFFFFFF);
        box(g, x0, y0, sx + 4, sy + 2, 2, 2, 0xFFFFFFFF);
        box(g, x0, y0, sx + 1.5F + ex * 0.5F, sy + 2.5F + ey * 0.5F, 1, 1, 0xFF101030);
        box(g, x0, y0, sx + 4.5F + ex * 0.5F, sy + 2.5F + ey * 0.5F, 1, 1, 0xFF101030);
    }

    @Override
    public int sample(float x, float y) {
        float cx = (x - OX) / CELL, cy = (y - OY) / CELL;
        if (dying == 0 && Math.abs(cx - cat.px() - 0.5F) < 0.8F && Math.abs(cy - cat.py() - 0.5F) < 0.8F) return 4;
        for (int i = 0; i < 4; i++) {
            Mover s = slimes[i];
            if (Math.abs(cx - s.px() - 0.5F) < 0.8F && Math.abs(cy - s.py() - 0.5F) < 0.8F) {
                if (s.state == State.EYES) return 4;
                return scared > 0 && s.fright ? 10 : COLOURS[i];
            }
        }
        int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy);
        if (ix < 0 || iy < 0 || ix >= MW || iy >= MH) return 0;
        char c = MAZE[iy].charAt(ix);
        if (c == '#') return 11;
        if (treats[iy][ix]) return 8;
        if (yarn[iy][ix]) return 15;
        if (fish > 0 && ix == 13 && iy == 13) return 7;
        return 0;
    }

    @Override
    public String status() {
        return "LIVES " + lives + "  LV " + (level + 1);
    }
}
