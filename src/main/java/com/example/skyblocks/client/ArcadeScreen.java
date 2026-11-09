package com.example.skyblocks.client;

import com.example.skyblocks.ArcadeBlockEntity;
import com.example.skyblocks.ArcadeScorePayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Playing at an arcade cabinet: Snake or Breakout (Tab to change), on a little screen in the cabinet's frame. A game
 * played to its end sends its score to the cabinet, which keeps the best.
 */
public class ArcadeScreen extends Screen {
    private static final int COLS = 24, ROWS = 18, CELL = 8, W = COLS * CELL, H = ROWS * CELL;
    private static final String[] NAMES = {"SNAKE", "BREAKOUT"};
    private enum Mode { TITLE, PLAYING, OVER }

    private final BlockPos pos;
    private final RandomSource random = RandomSource.create();
    private int game;
    private Mode mode = Mode.TITLE;
    private int score, ticks;
    private boolean left, right;
    private int left0, top0;

    // ---- snake ----
    private final ArrayDeque<int[]> body = new ArrayDeque<>();
    private final ArrayDeque<int[]> turns = new ArrayDeque<>();
    private int dx = 1, dy = 0, foodX, foodY, moveEvery, sinceMove;

    // ---- breakout ----
    private static final int BRICK_COLS = 12, BRICK_ROWS = 6, BRICK_W = W / BRICK_COLS, BRICK_H = 7, BRICK_TOP = 18;
    private static final int PADDLE_W = 34, PADDLE_Y = H - 10;
    private static final int[] ROW_COLOURS = {0xFFFF4D5E, 0xFFFF9A2E, 0xFFFFE74A, 0xFF5BFF6A, 0xFF4DA6FF, 0xFFB45BFF};
    private final boolean[] bricks = new boolean[BRICK_COLS * BRICK_ROWS];
    private float paddle, paddle0, ballX, ballY, ballX0, ballY0, vx, vy;
    private int lives;
    private boolean ballHeld;
    private double mouseX = -1;

    public ArcadeScreen(BlockPos pos) {
        super(Component.translatable("block.skyblocks.arcade"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        left0 = (width - W) / 2;
        top0 = (height - H) / 2 + 6;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void start() {
        score = 0;
        ticks = 0;
        mode = Mode.PLAYING;
        if (game == 0) {
            body.clear();
            turns.clear();
            for (int i = 0; i < 4; i++) {
                body.addLast(new int[] {COLS / 2 - i, ROWS / 2});
            }
            dx = 1;
            dy = 0;
            moveEvery = 4;
            sinceMove = 0;
            placeFood();
        } else {
            java.util.Arrays.fill(bricks, true);
            paddle = paddle0 = (W - PADDLE_W) / 2.0F;
            lives = 3;
            holdBall();
        }
    }

    private void over() {
        mode = Mode.OVER;
        if (score > 0) {
            PacketDistributor.sendToServer(new ArcadeScorePayload(pos, game, score));
        }
    }

    // ================= snake =================

    private void placeFood() {
        for (int tries = 0; tries < 1000; tries++) {
            int x = random.nextInt(COLS), y = random.nextInt(ROWS);
            boolean free = true;
            for (int[] b : body) {
                if (b[0] == x && b[1] == y) {
                    free = false;
                    break;
                }
            }
            if (free) {
                foodX = x;
                foodY = y;
                return;
            }
        }
    }

    private void turn(int x, int y) {
        int[] last = turns.isEmpty() ? new int[] {dx, dy} : turns.peekLast();
        if (turns.size() < 3 && !(last[0] == -x && last[1] == -y) && !(last[0] == x && last[1] == y)) {
            turns.addLast(new int[] {x, y});
        }
    }

    private void tickSnake() {
        if (++sinceMove < moveEvery) {
            return;
        }
        sinceMove = 0;
        if (!turns.isEmpty()) {
            int[] t = turns.pollFirst();
            dx = t[0];
            dy = t[1];
        }
        int[] head = body.peekFirst();
        int nx = head[0] + dx, ny = head[1] + dy;
        boolean eats = nx == foodX && ny == foodY;
        if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS) {
            over();
            return;
        }
        int i = 0, keep = eats ? body.size() : body.size() - 1;
        for (int[] b : body) {
            if (i++ < keep && b[0] == nx && b[1] == ny) {
                over();
                return;
            }
        }
        body.addFirst(new int[] {nx, ny});
        if (eats) {
            score += 10;
            if (body.size() % 5 == 0 && moveEvery > 2) {
                moveEvery--; // (faster as it grows)
            }
            placeFood();
        } else {
            body.removeLast();
        }
    }

    // ================= breakout =================

    private void holdBall() {
        ballHeld = true;
        ballX = ballX0 = paddle + PADDLE_W / 2.0F;
        ballY = ballY0 = PADDLE_Y - 3;
        vx = 0;
        vy = 0;
    }

    private void launch() {
        ballHeld = false;
        float a = (random.nextFloat() - 0.5F) * 1.2F;
        float speed = 2.6F;
        vx = Mth.sin(a) * speed;
        vy = -Mth.cos(a) * speed;
    }

    private void tickBreakout() {
        paddle0 = paddle;
        ballX0 = ballX;
        ballY0 = ballY;
        if (mouseX >= 0) {
            paddle = (float) (mouseX - left0 - PADDLE_W / 2.0);
        }
        if (left) {
            paddle -= 5.0F;
        }
        if (right) {
            paddle += 5.0F;
        }
        paddle = Mth.clamp(paddle, 0.0F, W - PADDLE_W);
        if (ballHeld) {
            ballX = paddle + PADDLE_W / 2.0F;
            if (paddle0 == paddle) {
                ballX0 = ballX;
            }
            return;
        }
        for (int step = 0; step < 4; step++) { // (in small steps, so it can't pass through a brick)
            ballX += vx / 4.0F;
            ballY += vy / 4.0F;
            if (ballX < 2) {
                ballX = 2;
                vx = Math.abs(vx);
            } else if (ballX > W - 2) {
                ballX = W - 2;
                vx = -Math.abs(vx);
            }
            if (ballY < 2) {
                ballY = 2;
                vy = Math.abs(vy);
            }
            if (vy > 0 && ballY >= PADDLE_Y - 2 && ballY <= PADDLE_Y + 3 && ballX >= paddle - 2 && ballX <= paddle + PADDLE_W + 2) {
                // off the paddle: the further from its middle, the more to the side
                float off = (ballX - (paddle + PADDLE_W / 2.0F)) / (PADDLE_W / 2.0F);
                float speed = Math.min(Mth.sqrt(vx * vx + vy * vy) * 1.02F, 5.0F);
                float a = Mth.clamp(off, -1.0F, 1.0F) * 1.05F;
                vx = Mth.sin(a) * speed;
                vy = -Mth.cos(a) * speed;
                ballY = PADDLE_Y - 2;
            }
            int col = (int) Math.floor(ballX / BRICK_W), row = (int) Math.floor((ballY - BRICK_TOP) / BRICK_H);
            if (row >= 0 && row < BRICK_ROWS && col >= 0 && col < BRICK_COLS && bricks[row * BRICK_COLS + col]) {
                bricks[row * BRICK_COLS + col] = false;
                score += (BRICK_ROWS - row) * 10;
                // back the way it came, on whichever side it came in by
                float px = ballX - vx / 4.0F, py = ballY - vy / 4.0F;
                int pcol = (int) Math.floor(px / BRICK_W), prow = (int) Math.floor((py - BRICK_TOP) / BRICK_H);
                if (prow != row) {
                    vy = -vy;
                } else {
                    vx = -vx;
                }
                ballX = px;
                ballY = py;
                boolean any = false;
                for (boolean b : bricks) {
                    any |= b;
                }
                if (!any) { // (all gone: a new wall, the ball a little faster)
                    java.util.Arrays.fill(bricks, true);
                    holdBall();
                    return;
                }
            }
            if (ballY > H + 4) {
                if (--lives <= 0) {
                    over();
                } else {
                    holdBall();
                }
                return;
            }
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getBlockEntity(pos) instanceof ArcadeBlockEntity)
                || mc.player == null || mc.player.distanceToSqr(pos.getCenter()) > 8 * 8) {
            onClose();
            return;
        }
        ticks++;
        if (mode == Mode.PLAYING) {
            if (game == 0) {
                tickSnake();
            } else {
                tickBreakout();
            }
        }
    }

    // ================= keys =================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (mode != Mode.PLAYING) {
            if (key == GLFW.GLFW_KEY_TAB || (mode == Mode.TITLE && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT))) {
                game = 1 - game;
                mode = Mode.TITLE;
            } else if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                start();
            }
            return true;
        }
        boolean l = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A, r = key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D;
        boolean u = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W, d = key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S;
        if (game == 0) {
            if (l) turn(-1, 0);
            if (r) turn(1, 0);
            if (u) turn(0, -1);
            if (d) turn(0, 1);
        } else {
            if (l || r) {
                mouseX = -1; // (keys take over from the mouse)
            }
            left |= l;
            right |= r;
            if (key == GLFW.GLFW_KEY_SPACE && ballHeld) {
                launch();
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            left = false;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            right = false;
        }
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (game == 1 && mode == Mode.PLAYING) {
            mouseX = x;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (mode != Mode.PLAYING) {
            start();
        } else if (game == 1 && ballHeld) {
            launch();
        }
        return true;
    }

    // ================= drawing =================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        int x0 = left0, y0 = top0;
        // the cabinet's frame round the screen
        g.fill(x0 - 14, y0 - 30, x0 + W + 14, y0 + H + 22, 0xFF1A1030);
        g.fill(x0 - 12, y0 - 28, x0 + W + 12, y0 - 10, 0xFF2B1650);
        drawCentred(g, NAMES[game], x0 + W / 2, y0 - 23, rainbow(ticks + partialTick));
        g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, 0xFF05050A);
        g.fill(x0, y0, x0 + W, y0 + H, 0xFF070B16);

        if (mode == Mode.TITLE) {
            drawTitle(g, x0, y0, partialTick);
        } else if (game == 0) {
            drawSnake(g, x0, y0);
        } else {
            drawBreakout(g, x0, y0, partialTick);
        }
        // the score along the top, the best under the screen
        g.drawString(font, "SCORE " + score, x0 + 3, y0 + 3, 0xFFFFFFFF, false);
        if (game == 1 && mode != Mode.TITLE) {
            String hearts = "❤".repeat(Math.max(lives, 0));
            g.drawString(font, hearts, x0 + W - 3 - font.width(hearts), y0 + 3, 0xFFFF4D5E, false);
        }
        ArcadeBlockEntity be = Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArcadeBlockEntity a ? a : null;
        if (be != null) {
            int best = be.best(game);
            String line = best > 0 ? "HI-SCORE " + best + " - " + be.bestBy(game) : "NO HI-SCORE YET";
            drawCentred(g, line, x0 + W / 2, y0 + H + 9, 0xFFE9F27A);
        }
        if (mode == Mode.OVER) {
            g.fill(x0, y0 + H / 2 - 22, x0 + W, y0 + H / 2 + 24, 0xC0000000);
            drawCentred(g, "GAME OVER", x0 + W / 2, y0 + H / 2 - 14, 0xFFFF4D5E);
            drawCentred(g, "SCORE " + score, x0 + W / 2, y0 + H / 2, 0xFFFFFFFF);
            if ((ticks / 10) % 2 == 0) {
                drawCentred(g, "SPACE: AGAIN   TAB: OTHER GAME", x0 + W / 2, y0 + H / 2 + 13, 0xFFAAAAAA);
            }
        }
        // the tube's scanlines
        for (int y = y0; y < y0 + H; y += 2) {
            g.fill(x0, y, x0 + W, y + 1, 0x22000000);
        }
    }

    private void drawTitle(GuiGraphics g, int x0, int y0, float partialTick) {
        float t = ticks + partialTick;
        drawCentred(g, NAMES[game], x0 + W / 2, y0 + 34, rainbow(t));
        if (game == 0) {
            drawCentred(g, "Arrows / WASD to steer", x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, "Eat, grow, don't bite yourself", x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        } else {
            drawCentred(g, "Mouse or arrows / A D: the paddle", x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, "Space or click: launch", x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        }
        if (((int) t / 10) % 2 == 0) {
            drawCentred(g, "PRESS SPACE", x0 + W / 2, y0 + 100, 0xFFFFFFFF);
        }
        drawCentred(g, "< TAB: " + NAMES[1 - game] + " >", x0 + W / 2, y0 + 124, 0xFF7788AA);
    }

    private void drawSnake(GuiGraphics g, int x0, int y0) {
        // the food, pulsing
        int pulse = (ticks / 4) % 2;
        g.fill(x0 + foodX * CELL + 1 - pulse, y0 + foodY * CELL + 1 - pulse, x0 + foodX * CELL + CELL - 1 + pulse, y0 + foodY * CELL + CELL - 1 + pulse, 0xFFFF3A4A);
        int i = 0, n = body.size();
        for (int[] b : body) {
            float f = 1.0F - (float) i / (n + 6);
            int green = (int) (120 + 135 * f), red = i == 0 ? 190 : (int) (40 + 60 * f);
            int c = 0xFF000000 | (red << 16) | (green << 8) | (i == 0 ? 120 : 60);
            g.fill(x0 + b[0] * CELL + 1, y0 + b[1] * CELL + 1, x0 + b[0] * CELL + CELL - 1, y0 + b[1] * CELL + CELL - 1, c);
            i++;
        }
    }

    private void drawBreakout(GuiGraphics g, int x0, int y0, float partialTick) {
        for (int r = 0; r < BRICK_ROWS; r++) {
            for (int c = 0; c < BRICK_COLS; c++) {
                if (bricks[r * BRICK_COLS + c]) {
                    int bx = x0 + c * BRICK_W, by = y0 + BRICK_TOP + r * BRICK_H;
                    g.fill(bx + 1, by + 1, bx + BRICK_W - 1, by + BRICK_H - 1, ROW_COLOURS[r]);
                    g.fill(bx + 1, by + 1, bx + BRICK_W - 1, by + 2, 0x55FFFFFF);
                }
            }
        }
        float p = Mth.lerp(partialTick, paddle0, paddle);
        g.fill((int) (x0 + p), y0 + PADDLE_Y, (int) (x0 + p + PADDLE_W), y0 + PADDLE_Y + 3, 0xFF9AD8FF);
        float bx = Mth.lerp(partialTick, ballX0, ballX), by = Mth.lerp(partialTick, ballY0, ballY);
        if (by < H) {
            g.fill((int) (x0 + bx - 2), (int) (y0 + by - 2), (int) (x0 + bx + 2), (int) (y0 + by + 2), 0xFFFFFFFF);
        }
        if (ballHeld && mode == Mode.PLAYING && (ticks / 10) % 2 == 0) {
            drawCentred(g, "SPACE: LAUNCH", x0 + W / 2, y0 + H / 2 + 20, 0xFFFFFFFF);
        }
    }

    private void drawCentred(GuiGraphics g, String s, int x, int y, int colour) {
        g.drawString(font, s, x - font.width(s) / 2, y, colour, false);
    }

    private static int rainbow(float t) {
        float h = (t / 60.0F) % 1.0F;
        return 0xFF000000 | Mth.hsvToRgb(h, 0.6F, 1.0F);
    }
}
