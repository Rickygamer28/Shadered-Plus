package com.example.skyblocks.client.arcade;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * Minesweeper: 16 x 11, 22 mines. Click to open (the first is always safe), right-click to flag - or the arrows, Space
 * and F. Clear it for a score that's higher the quicker you were.
 */
public class Minesweeper extends ArcadeGame {
    private static final int COLS = 16, ROWS = 11, CELL = 12, TOP = 12, MINES = 22;
    private final boolean[][] mine = new boolean[ROWS][COLS], open = new boolean[ROWS][COLS], flag = new boolean[ROWS][COLS];
    private final int[][] near = new int[ROWS][COLS];
    private boolean placed;
    private int cursorX, cursorY, opened, flags;
    private static final int[] NUMBER = {0, 10, 9, 6, 11, 7, 12, 4, 13};

    @Override
    public String name() {
        return "MINESWEEPER";
    }

    @Override
    public String[] help() {
        return new String[] {"Click: open   Right-click: flag", "or arrows, Space: open, F: flag"};
    }

    @Override
    public void start() {
        score = 0;
        over = won = false;
        placed = false;
        opened = flags = 0;
        ticks = 0;
        cursorX = COLS / 2;
        cursorY = ROWS / 2;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                mine[y][x] = open[y][x] = flag[y][x] = false;
                near[y][x] = 0;
            }
        }
    }

    private void place(int sx, int sy) {
        placed = true;
        int put = 0;
        while (put < MINES) {
            int x = random.nextInt(COLS), y = random.nextInt(ROWS);
            if (mine[y][x] || (Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1)) {
                continue; // (none on or round the first square opened)
            }
            mine[y][x] = true;
            put++;
        }
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int n = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < COLS && ny < ROWS && mine[ny][nx]) n++;
                    }
                }
                near[y][x] = n;
            }
        }
    }

    private void reveal(int x, int y) {
        if (over || flag[y][x] || open[y][x]) {
            return;
        }
        if (!placed) {
            place(x, y);
        }
        if (mine[y][x]) {
            over = true;
            open[y][x] = true;
            score = opened * 5;
            return;
        }
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        todo.add(new int[] {x, y});
        while (!todo.isEmpty()) {
            int[] c = todo.poll();
            if (open[c[1]][c[0]] || flag[c[1]][c[0]]) continue;
            open[c[1]][c[0]] = true;
            opened++;
            if (near[c[1]][c[0]] == 0) { // (nothing round it: its neighbours open too)
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = c[0] + dx, ny = c[1] + dy;
                        if (nx >= 0 && ny >= 0 && nx < COLS && ny < ROWS && !open[ny][nx]) todo.add(new int[] {nx, ny});
                    }
                }
            }
        }
        score = opened * 5;
        if (opened == COLS * ROWS - MINES) {
            won = over = true;
            score = 1000 + Math.max(0, 3000 - ticks / 20 * 10);
        }
    }

    private void toggleFlag(int x, int y) {
        if (!over && !open[y][x]) {
            flag[y][x] = !flag[y][x];
            flags += flag[y][x] ? 1 : -1;
        }
    }

    @Override
    public void tick() {
        if (placed && !over) {
            ticks++;
        }
    }

    @Override
    public void keyPressed(int key) {
        if (left(key)) cursorX = Math.max(0, cursorX - 1);
        if (right(key)) cursorX = Math.min(COLS - 1, cursorX + 1);
        if (up(key)) cursorY = Math.max(0, cursorY - 1);
        if (down(key)) cursorY = Math.min(ROWS - 1, cursorY + 1);
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) reveal(cursorX, cursorY);
        if (key == GLFW.GLFW_KEY_F) toggleFlag(cursorX, cursorY);
    }

    @Override
    public void mouseClicked(double mx, double my, int button) {
        int x = (int) Math.floor(mx / CELL), y = (int) Math.floor((my - TOP) / CELL);
        if (x < 0 || y < 0 || x >= COLS || y >= ROWS) return;
        cursorX = x;
        cursorY = y;
        if (button == 1) {
            toggleFlag(x, y);
        } else {
            reveal(x, y);
        }
    }

    private int colourOf(int x, int y) {
        if (!open[y][x]) {
            if (over && mine[y][x]) return 4;
            return flag[y][x] ? 3 : 13;
        }
        if (mine[y][x]) return 6;
        return near[y][x] == 0 ? 14 : NUMBER[near[y][x]];
    }

    @Override
    public void draw(GuiGraphics g, Font font, int x0, int y0, float pt) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int px = x * CELL, py = TOP + y * CELL;
                if (!open[y][x]) {
                    box(g, x0, y0, px, py, CELL - 1, CELL - 1, 0xFF5A6A8A);
                    box(g, x0, y0, px, py, CELL - 1, 1, 0xFF8A9AB8);
                    box(g, x0, y0, px, py, 1, CELL - 1, 0xFF8A9AB8);
                    if (flag[y][x]) {
                        box(g, x0, y0, px + 5, py + 2, 1, 8, 0xFF202020);
                        box(g, x0, y0, px + 6, py + 2, 4, 3, 0xFFFF3A4A);
                    } else if (over && mine[y][x]) {
                        box(g, x0, y0, px + 3, py + 3, 5, 5, 0xFF101010);
                    }
                } else {
                    box(g, x0, y0, px, py, CELL - 1, CELL - 1, mine[y][x] ? 0xFFFF3A4A : 0xFF1A2238);
                    if (mine[y][x]) {
                        box(g, x0, y0, px + 3, py + 3, 5, 5, 0xFF101010);
                    } else if (near[y][x] > 0) {
                        g.drawString(font, Integer.toString(near[y][x]), x0 + px + 3, y0 + py + 2, rgb(NUMBER[near[y][x]]), false);
                    }
                }
                if (x == cursorX && y == cursorY && !over) {
                    g.renderOutline(x0 + px - 1, y0 + py - 1, CELL + 1, CELL + 1, 0xFFFFE74A);
                }
            }
        }
    }

    @Override
    public int sample(float x, float y) {
        int cx = (int) (x / CELL), cy = (int) ((y - TOP) / CELL);
        if (cx < 0 || cy < 0 || cx >= COLS || cy >= ROWS) return 0;
        return colourOf(cx, cy);
    }

    @Override
    public String status() {
        return "MINES " + (MINES - flags) + "  " + ticks / 20 + "s";
    }
}
