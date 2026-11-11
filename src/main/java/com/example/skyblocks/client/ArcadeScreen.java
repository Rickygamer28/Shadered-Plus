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
import com.example.skyblocks.client.arcade.ArcadeGame.Sfx;
import org.lwjgl.glfw.GLFW;

/**
 * Playing at an arcade cabinet: Snake or Breakout (Tab to change), on a little screen in the cabinet's frame. A game
 * played to its end sends its score to the cabinet, which keeps the best.
 */
public class ArcadeScreen extends Screen {
    private static final int COLS = 24, ROWS = 18, CELL = 8, W = COLS * CELL, H = ROWS * CELL;
    private static final String[] NAMES = {"SNAKE", "BREAKOUT", "TETRIS", "WHISKER MAZE", "PONG", "FROGGER", "MINESWEEPER", "TIKKIN"};
    /** The games after the first three, each its own (client/arcade). */
    private final com.example.skyblocks.client.arcade.ArcadeGame[] extra = {new com.example.skyblocks.client.arcade.WhiskerMaze(),
            new com.example.skyblocks.client.arcade.Pong(), new com.example.skyblocks.client.arcade.Frogger(),
            new com.example.skyblocks.client.arcade.Minesweeper(), new com.example.skyblocks.client.arcade.Showdown()};

    private com.example.skyblocks.client.arcade.ArcadeGame ext() {
        return game >= 3 ? extra[game - 3] : null;
    }
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

    public BlockPos pos() {
        return pos;
    }

    // ================= versus: this cabinet linked to the one beside it (or back to back with it) =================

    private static final int SHOWDOWN = 7;
    /** On the fighting game here: sat down (the server knows - someone on it at a linked cabinet: a match). */
    private boolean seated;

    private com.example.skyblocks.client.arcade.Showdown showdown() {
        return (com.example.skyblocks.client.arcade.Showdown) extra[SHOWDOWN - 3];
    }

    /** Sitting down at the fighting game (said again every second), or getting up from it. */
    private void seat() {
        boolean want = game == SHOWDOWN;
        if (want && (!seated || ticks % 20 == 0)) {
            PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(com.example.skyblocks.ArcadeVersusPayload.SEAT, pos, 0));
            seated = true;
        } else if (!want && seated) {
            PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(com.example.skyblocks.ArcadeVersusPayload.UNSEAT, pos, 0));
            seated = false;
        }
    }

    /** A match (the server's word): a new fight, linked to the other cabinet, on its select screen - whatever was on. */
    public void startVersus(com.example.skyblocks.ArcadeVersusPayload p) {
        com.example.skyblocks.client.arcade.Showdown old = showdown();
        if (old.inVersus()) old.link().deactivate();
        game = SHOWDOWN;
        com.example.skyblocks.client.arcade.Showdown sd = new com.example.skyblocks.client.arcade.Showdown();
        extra[SHOWDOWN - 3] = sd;
        int id = p.match();
        String[] names = p.text().split("\\|", 2);
        var link = new com.example.skyblocks.client.arcade.Netplay(sd, id, p.side(), new com.example.skyblocks.client.arcade.Netplay.Link() {
            @Override
            public void send(int kind, int frame, int x, int y, byte[] data) {
                PacketDistributor.sendToServer(new com.example.skyblocks.ArcadeNetPayload(id, kind, frame, x, y, data == null ? new byte[0] : data));
            }

            @Override
            public void quit(String why) {
                PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(com.example.skyblocks.ArcadeVersusPayload.LEAVE, pos, id));
            }
        });
        sd.startVersus(link, p.seed(), names[0], names.length > 1 ? names[1] : "2P");
        link.activate();
        mode = Mode.PLAYING;
        score = 0;
        sfx(Sfx.COIN);
    }

    /** The match is off (the server's word: they left ...). */
    public void endVersus(com.example.skyblocks.ArcadeVersusPayload p) {
        com.example.skyblocks.client.arcade.Showdown sd = showdown();
        if (sd.inVersus() && sd.link().matchId() == p.match()) {
            sd.link().deactivate();
            sd.endVersus(p.text().isEmpty() ? "MATCH OVER" : p.text());
        }
    }

    @Override
    protected void init() {
        layout();
    }

    /** How big the cabinet's drawn: the same share of the window whatever the GUI scale (it's all drawn scaled by this). */
    private float scale = 1;

    private void layout() {
        // (the cabinet, frame and all, is W + 28 by H + 52: about four fifths of the window's height, or of its width)
        scale = Math.max(0.25F, Math.min(height * 0.84F / (H + 76), width * 0.9F / (W + 28)));
        left0 = Math.round((width / scale - W) / 2);
        top0 = Math.round((height / scale - H - 24) / 2 + 6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void start() {
        score = 0;
        ticks = 0;
        mode = Mode.PLAYING;
        sfx(game == 3 ? Sfx.MAZE_START : Sfx.START);
        if (ext() != null) {
            ext().start();
            return;
        }
        if (game == 2) {
            for (int[] row : board) {
                java.util.Arrays.fill(row, 0);
            }
            lines = 0;
            dropEvery = 20;
            sinceDrop = 0;
            nextPiece = random.nextInt(7);
            spawn();
            return;
        }
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

    private boolean canClaim() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.level.getBlockEntity(pos) instanceof ArcadeBlockEntity be
                && be.claimable(game, mc.player.getGameProfile().getName());
    }

    private void over() {
        mode = Mode.OVER;
        playQueued();
        sfx(ext() != null && ext().won() ? Sfx.WIN : Sfx.GAMEOVER);
        if (score > 0) {
            PacketDistributor.sendToServer(new ArcadeScorePayload(pos, game, score));
        }
    }

    /** A sound for the game being played (or the cabinet's menus). */
    private static void sfx(Sfx s) {
        ArcadeSounds.play(s);
    }

    private static void sfx(Sfx s, float pitch) {
        ArcadeSounds.play(s, pitch, 1.0F);
    }

    /** The sounds the game asked for since last time: played. */
    private void playQueued() {
        if (ext() == null) return;
        for (com.example.skyblocks.client.arcade.ArcadeGame.SoundCall c : ext().drainSounds()) ArcadeSounds.play(c.sfx(), c.pitch(), c.volume());
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
            sfx(Sfx.SNAKE_DIE);
            over();
            return;
        }
        int i = 0, keep = eats ? body.size() : body.size() - 1;
        for (int[] b : body) {
            if (i++ < keep && b[0] == nx && b[1] == ny) {
                sfx(Sfx.SNAKE_DIE);
                over();
                return;
            }
        }
        body.addFirst(new int[] {nx, ny});
        if (eats) {
            score += 10;
            sfx(Sfx.SNAKE_EAT, Math.min(2.0F, 0.8F + body.size() * 0.02F)); // (higher as it grows)
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
        sfx(Sfx.PADDLE, 1.5F);
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
                sfx(Sfx.WALL);
            } else if (ballX > W - 2) {
                ballX = W - 2;
                vx = -Math.abs(vx);
                sfx(Sfx.WALL);
            }
            if (ballY < 2) {
                ballY = 2;
                vy = Math.abs(vy);
                sfx(Sfx.WALL);
            }
            if (vy > 0 && ballY >= PADDLE_Y - 2 && ballY <= PADDLE_Y + 3 && ballX >= paddle - 2 && ballX <= paddle + PADDLE_W + 2) {
                // off the paddle: the further from its middle, the more to the side
                float off = (ballX - (paddle + PADDLE_W / 2.0F)) / (PADDLE_W / 2.0F);
                float speed = Math.min(Mth.sqrt(vx * vx + vy * vy) * 1.02F, 5.0F);
                float a = Mth.clamp(off, -1.0F, 1.0F) * 1.05F;
                vx = Mth.sin(a) * speed;
                vy = -Mth.cos(a) * speed;
                ballY = PADDLE_Y - 2;
                sfx(Sfx.PADDLE);
            }
            int col = (int) Math.floor(ballX / BRICK_W), row = (int) Math.floor((ballY - BRICK_TOP) / BRICK_H);
            if (row >= 0 && row < BRICK_ROWS && col >= 0 && col < BRICK_COLS && bricks[row * BRICK_COLS + col]) {
                bricks[row * BRICK_COLS + col] = false;
                score += (BRICK_ROWS - row) * 10;
                sfx(Sfx.BRICK, 0.8F + (BRICK_ROWS - row) * 0.1F); // (the higher rows: higher notes)
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
                    sfx(Sfx.LEVELUP);
                    java.util.Arrays.fill(bricks, true);
                    holdBall();
                    return;
                }
            }
            if (ballY > H + 4) {
                sfx(Sfx.LOSE);
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
        seat();
        if (mode == Mode.PLAYING && ext() != null) {
            if (ext().rate() == 20) ext().tick(); // (a faster game: ticked from render, on its own clock - see runFast)
            playQueued();
            score = ext().score();
            if (ext().over()) {
                over();
            }
        } else if (mode == Mode.PLAYING) {
            if (game == 0) {
                tickSnake();
            } else if (game == 1) {
                tickBreakout();
            } else {
                tickTetris();
            }
        }
        // what's on the screen, to the cabinet (everyone near it sees it on its screen)
        if (mode != Mode.TITLE && ticks % 2 == 0) {
            pictureDue = true; // (taken as it's next drawn: ten a second)
        } else if (mode == Mode.TITLE && showing) {
            stopShowing();
        }
    }

    // ================= tetris =================

    private static final int TW = 10, TH = 20, TCELL = 7;
    /** The seven pieces: their cells (x, y) in a box of the given size, and their colour (the palette's). */
    private static final int[][][] PIECES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {1, 1}, {2, 1}}, {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}};
    private static final int[] PIECE_BOX = {4, 2, 3, 3, 3, 3, 3};
    private static final int[] PIECE_COLOUR = {12, 8, 11, 9, 6, 10, 7};
    private final int[][] board = new int[TH][TW];
    private int[][] piece;
    private int pieceType, nextPiece, px, py, lines, dropEvery, sinceDrop;

    private void spawn() {
        pieceType = nextPiece;
        nextPiece = random.nextInt(7);
        piece = new int[4][];
        for (int i = 0; i < 4; i++) {
            piece[i] = PIECES[pieceType][i].clone();
        }
        px = (TW - PIECE_BOX[pieceType]) / 2;
        py = 0;
        if (!fits(piece, px, py)) {
            over();
        }
    }

    private boolean fits(int[][] cells, int x, int y) {
        for (int[] c : cells) {
            int cx = x + c[0], cy = y + c[1];
            if (cx < 0 || cx >= TW || cy >= TH || (cy >= 0 && board[cy][cx] != 0)) {
                return false;
            }
        }
        return true;
    }

    private void tetrisMove(int dx) {
        if (fits(piece, px + dx, py)) {
            px += dx;
            sfx(Sfx.MOVE);
        }
    }

    private void tetrisRotate() {
        int n = PIECE_BOX[pieceType];
        if (n == 2) {
            return; // (the square)
        }
        int[][] turned = new int[4][];
        for (int i = 0; i < 4; i++) {
            turned[i] = new int[] {n - 1 - piece[i][1], piece[i][0]};
        }
        for (int kick : new int[] {0, -1, 1, -2, 2}) { // (pushed off a wall or the stack if it must)
            if (fits(turned, px + kick, py)) {
                piece = turned;
                px += kick;
                sfx(Sfx.ROTATE);
                return;
            }
        }
    }

    /** One row down; landed: kept on the board. Returns whether it moved. */
    private boolean tetrisDown(boolean byPlayer) {
        if (fits(piece, px, py + 1)) {
            py++;
            if (byPlayer) {
                score += 1;
            }
            sinceDrop = 0;
            return true;
        }
        land();
        return false;
    }

    /** How far down the piece would fall from here. */
    private int ghostY() {
        int y = py;
        while (fits(piece, px, y + 1)) {
            y++;
        }
        return y;
    }

    private void tetrisHardDrop() {
        int rows = 0;
        while (fits(piece, px, py + 1)) {
            py++;
            rows++;
        }
        score += rows * 2;
        sfx(Sfx.DROP);
        land();
    }

    private void land() {
        for (int[] c : piece) {
            if (py + c[1] >= 0) {
                board[py + c[1]][px + c[0]] = PIECE_COLOUR[pieceType];
            }
        }
        int cleared = 0;
        for (int y = TH - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < TW; x++) {
                full &= board[y][x] != 0;
            }
            if (full) {
                for (int yy = y; yy > 0; yy--) {
                    board[yy] = board[yy - 1].clone();
                }
                board[0] = new int[TW];
                cleared++;
                y++; // (the row that fell into this one, looked at again)
            }
        }
        int level = lines / 10;
        if (cleared == 4) sfx(Sfx.FOUR);
        else if (cleared > 0) sfx(Sfx.LINE, 0.9F + cleared * 0.1F);
        else sfx(Sfx.LAND);
        if ((lines + cleared) / 10 > level) sfx(Sfx.LEVELUP);
        score += new int[] {0, 100, 300, 500, 800}[cleared] * (level + 1);
        lines += cleared;
        dropEvery = Math.max(2, 20 - (lines / 10) * 2); // (faster every ten lines)
        sinceDrop = 0;
        spawn();
    }

    private void tickTetris() {
        if (++sinceDrop >= dropEvery) {
            sinceDrop = 0;
            tetrisDown(false);
        }
    }

    private void drawTetris(GuiGraphics g, int x0, int y0) {
        int bx = x0 + (W - TW * TCELL) / 2, by = y0 + 2;
        g.fill(bx - 2, by - 1, bx + TW * TCELL + 2, by + TH * TCELL + 2, 0xFF3A4A6A);
        g.fill(bx, by, bx + TW * TCELL, by + TH * TCELL, 0xFF070B16);
        int[] pal = com.example.skyblocks.ArcadeBlockEntity.PALETTE;
        for (int y = 0; y < TH; y++) {
            for (int x = 0; x < TW; x++) {
                if (board[y][x] != 0) {
                    cell(g, bx + x * TCELL, by + y * TCELL, pal[board[y][x]]);
                }
            }
        }
        if (piece != null && mode == Mode.PLAYING) {
            // where it will land: its outline at the bottom of its fall
            int gy = ghostY();
            for (int[] c : piece) {
                if (gy + c[1] >= 0) {
                    int cx = bx + (px + c[0]) * TCELL, cy = by + (gy + c[1]) * TCELL, col = 0xFF000000 | pal[PIECE_COLOUR[pieceType]];
                    g.fill(cx, cy, cx + TCELL - 1, cy + TCELL - 1, (col & 0x00FFFFFF) | 0x40000000);
                    g.renderOutline(cx, cy, TCELL - 1, TCELL - 1, (col & 0x00FFFFFF) | 0xB0000000);
                }
            }
            for (int[] c : piece) {
                if (py + c[1] >= 0) {
                    cell(g, bx + (px + c[0]) * TCELL, by + (py + c[1]) * TCELL, pal[PIECE_COLOUR[pieceType]]);
                }
            }
        }
        // the next piece, and the lines
        int nx = bx + TW * TCELL + 10, ny = by + 14;
        g.drawString(font, "NEXT", nx, by + 2, 0xFFAAAAAA, false);
        for (int[] c : PIECES[nextPiece]) {
            cell(g, nx + c[0] * TCELL, ny + c[1] * TCELL, pal[PIECE_COLOUR[nextPiece]]);
        }
        g.drawString(font, "LINES", nx, by + 40, 0xFFAAAAAA, false);
        g.drawString(font, Integer.toString(lines), nx, by + 50, 0xFFFFFFFF, false);
    }

    private static void cell(GuiGraphics g, int x, int y, int rgb) {
        g.fill(x, y, x + TCELL - 1, y + TCELL - 1, 0xFF000000 | rgb);
        g.fill(x, y, x + TCELL - 1, y + 1, 0x55FFFFFF);
    }

    // ================= the cabinet's screen =================

    private boolean showing;

    /** The game now as the cabinet's screen shows it: 32 x 24 cells, each a colour of the palette (rows 0-1 left for the name). */
    private byte[] frame() {
        int fw = com.example.skyblocks.ArcadeFramePayload.WIDTH, fh = com.example.skyblocks.ArcadeFramePayload.HEIGHT;
        byte[] f = new byte[fw * fh];
        java.util.function.IntBinaryOperator idx = (x, y) -> y * fw + x;
        if (ext() != null) { // (each cell: the colour at its middle, the field over rows 2 and on)
            for (int cy = 2; cy < fh; cy++) {
                for (int cx = 0; cx < fw; cx++) {
                    f[idx.applyAsInt(cx, cy)] = (byte) (ext().sample((cx + 0.5F) * W / fw, (cy - 2 + 0.5F) * H / (fh - 2.0F)) & 15);
                }
            }
            return f;
        }
        if (game == 0) {
            for (int y = 2; y < 2 + ROWS + 2; y++) {
                for (int x = 3; x < 3 + COLS + 2; x++) {
                    boolean edge = x == 3 || y == 2 || x == 3 + COLS + 1 || y == 2 + ROWS + 1;
                    f[idx.applyAsInt(x, y)] = (byte) (edge ? 13 : 14);
                }
            }
            f[idx.applyAsInt(4 + foodX, 3 + foodY)] = 3;
            int i = 0;
            for (int[] b : body) {
                f[idx.applyAsInt(4 + b[0], 3 + b[1])] = (byte) (i++ == 0 ? 2 : 1);
            }
        } else if (game == 1) {
            for (int y = 2; y < fh; y++) {
                for (int x = 0; x < fw; x++) {
                    float cx = (x + 0.5F) * W / fw, cy = (y - 2 + 0.5F) * H / (fh - 2);
                    int col = (int) (cx / BRICK_W), row = (int) Math.floor((cy - BRICK_TOP) / BRICK_H);
                    byte v = 14;
                    if (row >= 0 && row < BRICK_ROWS && col < BRICK_COLS && bricks[row * BRICK_COLS + col]) {
                        v = (byte) (6 + row % 6);
                    }
                    if (Math.abs(cy - PADDLE_Y) < H / (fh - 2.0F) * 0.6F && cx >= paddle && cx <= paddle + PADDLE_W) {
                        v = 5;
                    }
                    f[idx.applyAsInt(x, y)] = v;
                }
            }
            if (ballY < H) {
                int bx = Mth.clamp((int) (ballX * fw / W), 0, fw - 1), by = Mth.clamp(2 + (int) (ballY * (fh - 2) / H), 2, fh - 1);
                f[idx.applyAsInt(bx, by)] = 4;
            }
        } else {
            int ox = 10, oy = 2;
            for (int y = 0; y < TH + 1; y++) {
                f[idx.applyAsInt(ox, oy + y)] = 13;
                f[idx.applyAsInt(ox + TW + 1, oy + y)] = 13;
            }
            for (int x = 0; x < TW + 2; x++) {
                f[idx.applyAsInt(ox + x, oy + TH)] = 13;
            }
            for (int y = 0; y < TH; y++) {
                for (int x = 0; x < TW; x++) {
                    f[idx.applyAsInt(ox + 1 + x, oy + y)] = (byte) (board[y][x] != 0 ? board[y][x] : 14);
                }
            }
            if (piece != null && mode == Mode.PLAYING) {
                int gy = ghostY();
                for (int[] c : piece) {
                    if (gy + c[1] >= 0 && gy + c[1] < TH) {
                        f[idx.applyAsInt(ox + 1 + px + c[0], oy + gy + c[1])] = 13;
                    }
                }
                for (int[] c : piece) {
                    if (py + c[1] >= 0 && py + c[1] < TH) {
                        f[idx.applyAsInt(ox + 1 + px + c[0], oy + py + c[1])] = (byte) PIECE_COLOUR[pieceType];
                    }
                }
            }
            for (int[] c : PIECES[nextPiece]) {
                f[idx.applyAsInt(25 + c[0], 4 + c[1])] = (byte) PIECE_COLOUR[nextPiece];
            }
        }
        if (mode == Mode.OVER && (ticks / 10) % 2 == 0) { // (game over: a blinking red line across)
            for (int x = 0; x < fw; x++) {
                f[idx.applyAsInt(x, fh / 2)] = 6;
            }
        }
        return f;
    }

    private void stopShowing() {
        showing = false;
        if (RenderTest.keepPicture()) return; // (the render test: looking at the cabinet from the room just after)
        PacketDistributor.sendToServer(com.example.skyblocks.ArcadeFramePayload.stopped(pos, game, score, ArcadeSounds.takeForPicture()));
    }

    // ---- the picture: the game drawn again, small, into a picture of its own - what the cabinet shows the room ----

    private static final int PIC_W = 96, PIC_H = 72;
    private boolean pictureDue, pictureBroken;
    private com.mojang.blaze3d.pipeline.TextureTarget shot;

    /** The game as it is now, to the cabinet (and everyone near it): its picture, and what's held on the controls. */
    private void sendPicture(GuiGraphics g, float partialTick, float fastPartial) {
        int[] px = null;
        int w = PIC_W, h = PIC_H;
        if (!pictureBroken) {
            try {
                px = capture(g, partialTick, fastPartial);
            } catch (Throwable e) { // (can't draw it off the screen here: the plain little picture instead, from now on)
                pictureBroken = true;
                com.example.skyblocks.SkyBlocksMod.LOGGER.warn("Arcade: couldn't take the cabinet's picture, using the simple one: {}", e.toString());
            }
        }
        if (px == null) {
            byte[] cells = frame();
            w = com.example.skyblocks.ArcadeFramePayload.WIDTH; // (32 x 24, the palette's colours)
            h = com.example.skyblocks.ArcadeFramePayload.HEIGHT;
            px = new int[w * h];
            for (int i = 0; i < px.length && i < cells.length; i++) px[i] = ArcadeBlockEntity.PALETTE[cells[i] & 15];
        }
        byte[] data = ArcadePicture.encode(px, w, h);
        if (data.length > com.example.skyblocks.ArcadeFramePayload.MAX_BYTES) return;
        PacketDistributor.sendToServer(new com.example.skyblocks.ArcadeFramePayload(pos, game, score, "", w, h, data, ArcadeSounds.takeForPicture(),
                inputBits()));
        showing = true;
    }

    /** The screen's insides drawn into a picture of their own (W x H), then halved: PIC_W x PIC_H, 0xRRGGBB. */
    private int[] capture(GuiGraphics g, float partialTick, float fastPartial) {
        Minecraft mc = Minecraft.getInstance();
        g.flush(); // (whatever's waiting to be drawn on the real screen: drawn there first)
        if (shot == null) shot = new com.mojang.blaze3d.pipeline.TextureTarget(W, H, true, Minecraft.ON_OSX);
        shot.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        shot.clear(Minecraft.ON_OSX);
        shot.bindWrite(true);
        org.joml.Matrix4f projection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0.0F, W, H, 0.0F, 1000.0F, 21000.0F),
                com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
        org.joml.Matrix4fStack view = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.translation(0.0F, 0.0F, -11000.0F);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            GuiGraphics pic = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            drawScreen(pic, 0, 0, partialTick, fastPartial, true);
            pic.flush();
        } finally {
            view.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(projection, sorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
        int[] full = new int[W * H];
        try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(W, H, false)) {
            com.mojang.blaze3d.systems.RenderSystem.bindTexture(shot.getColorTextureId());
            img.downloadTexture(0, false);
            img.flipY();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    full[y * W + x] = (abgr & 255) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 255);
                }
            }
        }
        int[] out = new int[PIC_W * PIC_H];
        int sx = W / PIC_W, sy = H / PIC_H;
        for (int y = 0; y < PIC_H; y++) {
            for (int x = 0; x < PIC_W; x++) {
                int r = 0, gr = 0, b = 0;
                for (int yy = 0; yy < sy; yy++) {
                    for (int xx = 0; xx < sx; xx++) {
                        int c = full[(y * sy + yy) * W + x * sx + xx];
                        r += c >> 16 & 255;
                        gr += c >> 8 & 255;
                        b += c & 255;
                    }
                }
                int n = sx * sy;
                out[y * PIC_W + x] = (r / n) << 16 | (gr / n) << 8 | (b / n);
            }
        }
        return out;
    }

    /** The game's keys held now (whatever game): the way, as the cabinet's stick; six buttons. (ArcadeRenderer's) */
    private final java.util.Set<Integer> heldKeys = new java.util.HashSet<>();

    private int inputBits() {
        int bits = 0;
        for (int k : heldKeys) {
            if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_A) bits |= 1;
            else if (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_D) bits |= 2;
            else if (k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_W) bits |= 4;
            else if (k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_S) bits |= 8;
            else if (k == GLFW.GLFW_KEY_U) bits |= 16;
            else if (k == GLFW.GLFW_KEY_I) bits |= 32;
            else if (k == GLFW.GLFW_KEY_L || k == GLFW.GLFW_KEY_X) bits |= 64;
            else if (k == GLFW.GLFW_KEY_J) bits |= 128;
            else if (k == GLFW.GLFW_KEY_K) bits |= 256;
            else if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER) bits |= 512;
        }
        return bits;
    }

    @Override
    public void removed() {
        com.example.skyblocks.client.arcade.Showdown sd = showdown();
        if (sd.inVersus()) { // (walking away from a match: it's off)
            var link = sd.link();
            link.deactivate();
            PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(com.example.skyblocks.ArcadeVersusPayload.LEAVE, pos, link.matchId()));
            sd.endVersus("");
        }
        if (seated) {
            seated = false;
            PacketDistributor.sendToServer(com.example.skyblocks.ArcadeVersusPayload.of(com.example.skyblocks.ArcadeVersusPayload.UNSEAT, pos, 0));
        }
        if (showing) {
            stopShowing();
        }
        playMusic(null);
        super.removed();
    }

    // the game's music (a loop: the fighting game's select screen and stages)
    private net.minecraft.client.resources.sounds.SoundInstance musicNow;
    private String musicName;

    private void playMusic(String name) {
        if (ArcadeSounds.muted()) name = null;
        if (java.util.Objects.equals(name, musicName)) return;
        if (musicNow != null) Minecraft.getInstance().getSoundManager().stop(musicNow);
        musicName = name;
        musicNow = ArcadeSounds.music(name);
    }

    // ================= keys =================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (mode != Mode.PLAYING) {
            if (key == GLFW.GLFW_KEY_TAB || (mode == Mode.TITLE && key == GLFW.GLFW_KEY_RIGHT)) {
                game = (game + 1) % NAMES.length;
                mode = Mode.TITLE;
                sfx(Sfx.BLIP);
            } else if (mode == Mode.TITLE && key == GLFW.GLFW_KEY_LEFT) {
                game = (game + NAMES.length - 1) % NAMES.length;
                sfx(Sfx.BLIP);
            } else if (key == GLFW.GLFW_KEY_M) {
                ArcadeSounds.toggle();
            } else if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
                start();
            } else if (key == GLFW.GLFW_KEY_T && canClaim()) { // (the best score's holder: its trophy)
                PacketDistributor.sendToServer(new com.example.skyblocks.TrophyClaimPayload(pos, game));
            } else if (key == GLFW.GLFW_KEY_C) {
                openControls();
            }
            return true;
        }
        // playing: whatever this key is set to do in this game, as the game's own key for it (M, if it's not: the sound)
        if (key == GLFW.GLFW_KEY_M && ArcadeControls.fromKey(game, key).isEmpty()) {
            ArcadeSounds.toggle();
            return true;
        }
        for (int k : ArcadeControls.fromKey(game, key)) {
            gameKey(k);
        }
        return true;
    }

    /** (the render test: a game started, its own keys pressed) */
    public void testPlay(int g, int... keys) {
        game = g;
        start();
        for (int k : keys) gameKey(k);
    }

    public void testKey(int key) {
        gameKey(key);
    }

    public void testControls() {
        openControls();
    }

    private void openControls() {
        Minecraft.getInstance().setScreen(new ArcadeControlsScreen(this, game));
    }

    /** A game's own key, pressed. */
    private void gameKey(int key) {
        heldKeys.add(key);
        if (ext() != null) {
            ext().keyPressed(key);
            return;
        }
        boolean l = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A, r = key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D;
        boolean u = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W, d = key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S;
        if (game == 2) {
            if (l) tetrisMove(-1);
            if (r) tetrisMove(1);
            if (u || key == GLFW.GLFW_KEY_X) tetrisRotate();
            if (d) tetrisDown(true);
            if (key == GLFW.GLFW_KEY_SPACE) tetrisHardDrop();
            return;
        }
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
        return;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (mode == Mode.PLAYING) {
            for (int k : ArcadeControls.fromKey(game, key)) {
                gameKeyUp(k);
            }
        }
        return true;
    }

    /** A game's own key, let go. */
    private void gameKeyUp(int key) {
        heldKeys.remove(key);
        if (ext() != null) {
            ext().keyReleased(key);
            return;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            left = false;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            right = false;
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        x /= scale; // (into the cabinet's own, scaled, units)
        y /= scale;
        if (game == 1 && mode == Mode.PLAYING) {
            mouseX = x;
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        x /= scale;
        y /= scale;
        if (mode == Mode.PLAYING && ext() != null) {
            ext().mouseClicked(x - left0, y - top0, button);
        } else if (mode != Mode.PLAYING) {
            start();
        } else if (game == 1 && ballHeld) {
            launch();
        }
        return true;
    }

    // ================= drawing =================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0x70000000); // (dimmed, not hidden: the room - its big screens - still there round the cabinet)
    }

    private final ArcadeControls.Pad pad = new ArcadeControls.Pad();

    // a game faster than the world's 20 ticks (the fighting game, at 60): ticked here, as often as its clock says
    private long fastLast = -1;
    private double fastDue;

    /** Ticks a faster game for the time since the last frame (a few at most: a slow frame slows it, not skips it);
     *  how far it is into its next tick. */
    private float runFast() {
        var g = ext();
        if (mode != Mode.PLAYING || g == null || g.rate() == 20) {
            fastLast = -1;
            fastDue = 0;
            return 0;
        }
        long now = System.nanoTime();
        if (fastLast >= 0) fastDue += (now - fastLast) / 1.0E9 * g.rate();
        fastLast = now;
        fastDue = Math.min(fastDue, 4);
        while (fastDue >= 1 && mode == Mode.PLAYING) {
            fastDue -= 1;
            g.tick();
            playQueued();
            score = g.score();
            if (g.over()) {
                over();
                break;
            }
        }
        return (float) Math.min(1, fastDue);
    }

    /** A controller: in a game, what its buttons are set to there; at the title, start, change game, the controls. */
    private void pollPad() {
        java.util.List<Integer> pressed = new java.util.ArrayList<>(), released = new java.util.ArrayList<>();
        pad.poll(pressed, released);
        for (int input : pressed) {
            if (input == ArcadeControls.BACK) {
                onClose();
                return;
            }
            if (mode == Mode.PLAYING) {
                for (int k : ArcadeControls.fromPad(game, input)) gameKey(k);
            } else if (input == ArcadeControls.START || input == ArcadeControls.A) {
                start();
            } else if (input == ArcadeControls.DLEFT || input == ArcadeControls.LS_LEFT || input == ArcadeControls.LB) {
                game = (game + NAMES.length - 1) % NAMES.length;
                mode = Mode.TITLE;
                sfx(Sfx.BLIP);
            } else if (input == ArcadeControls.DRIGHT || input == ArcadeControls.LS_RIGHT || input == ArcadeControls.RB) {
                game = (game + 1) % NAMES.length;
                mode = Mode.TITLE;
                sfx(Sfx.BLIP);
            } else if (input == ArcadeControls.Y && canClaim()) {
                PacketDistributor.sendToServer(new com.example.skyblocks.TrophyClaimPayload(pos, game));
            } else if (input == ArcadeControls.X) {
                openControls();
                return;
            } else if (input == ArcadeControls.B) {
                onClose();
                return;
            }
        }
        long now = System.currentTimeMillis();
        for (int input : pressed) heldSince[input] = now;
        if (mode == Mode.PLAYING) {
            for (int input : released) {
                for (int k : ArcadeControls.fromPad(game, input)) gameKeyUp(k);
            }
            // held down: it repeats, as a held key does
            for (int input = 0; input < ArcadeControls.PAD_INPUTS; input++) {
                if (pad.down[input] && now - heldSince[input] > 260 && now - lastRepeat[input] > 55) {
                    lastRepeat[input] = now;
                    for (int k : ArcadeControls.fromPad(game, input)) gameKey(k);
                }
            }
        }
    }

    private final long[] heldSince = new long[ArcadeControls.PAD_INPUTS], lastRepeat = new long[ArcadeControls.PAD_INPUTS];

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        pollPad();
        float fastPartial = runFast();
        if (pictureDue) {
            pictureDue = false;
            if (mode != Mode.TITLE) sendPicture(g, partialTick, fastPartial);
        }
        playMusic(mode == Mode.PLAYING && ext() != null ? ext().music() : null);
        super.render(g, mx, my, partialTick);
        layout();
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        try {
            drawCabinet(g, partialTick, fastPartial);
        } finally {
            g.pose().popPose();
        }
    }

    private void drawCabinet(GuiGraphics g, float partialTick, float fastPartial) {
        int x0 = left0, y0 = top0;
        // the cabinet's frame round the screen
        g.fill(x0 - 14, y0 - 30, x0 + W + 14, y0 + H + 46, 0xFF1A1030);
        drawPanel(g, x0, y0 + H + 20);
        g.fill(x0 - 12, y0 - 28, x0 + W + 12, y0 - 10, 0xFF2B1650);
        drawCentred(g, NAMES[game], x0 + W / 2, y0 - 23, rainbow(ticks + partialTick));
        g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, 0xFF05050A);
        drawScreen(g, x0, y0, partialTick, fastPartial, false);
        ArcadeBlockEntity be = Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArcadeBlockEntity a ? a : null;
        if (be != null) {
            int best = be.best(game);
            String line = best > 0 ? "HI-SCORE " + best + " - " + be.bestBy(game) : "NO HI-SCORE YET";
            drawCentred(g, line, x0 + W / 2, y0 + H + 9, 0xFFE9F27A);
            if (mode != Mode.PLAYING && canClaim() && (ticks / 10) % 2 == 0) {
                drawCentred(g, "T: CLAIM YOUR TROPHY!", x0 + W / 2, y0 + H - 12, 0xFFFFC23A);
            }
        }
    }

    /** What's on the screen (W x H from x0, y0): the game, its score, game over. For a picture: no scissor, no scanlines
     *  (the cabinet's tube has its own). */
    private void drawScreen(GuiGraphics g, int x0, int y0, float partialTick, float fastPartial, boolean picture) {
        g.fill(x0, y0, x0 + W, y0 + H, 0xFF070B16);
        if (mode == Mode.TITLE) {
            drawTitle(g, x0, y0, partialTick);
        } else if (ext() != null) {
            if (!picture) { // (the scissor's in the screen's own units: worked out without the scale on, so it's right either way)
                g.pose().pushPose();
                g.pose().setIdentity();
                g.enableScissor(Math.round(x0 * scale), Math.round(y0 * scale), Math.round((x0 + W) * scale), Math.round((y0 + H) * scale));
                g.pose().popPose();
            }
            ext().draw(g, font, x0, y0, ext().rate() == 20 ? partialTick : fastPartial);
            if (!picture) g.disableScissor();
        } else if (game == 0) {
            drawSnake(g, x0, y0);
        } else if (game == 2) {
            drawTetris(g, x0, y0);
        } else {
            drawBreakout(g, x0, y0, partialTick);
        }
        // the score along the top, the best under the screen
        g.drawString(font, "SCORE " + score, x0 + 3, y0 + 3, 0xFFFFFFFF, false);
        if (ext() != null && mode != Mode.TITLE && !ext().status().isEmpty()) {
            String st = ext().status();
            g.drawString(font, st, x0 + W - 3 - font.width(st), y0 + 3, 0xFFFFFFFF, false);
        }
        if (game == 1 && mode != Mode.TITLE) {
            String hearts = "❤".repeat(Math.max(lives, 0));
            g.drawString(font, hearts, x0 + W - 3 - font.width(hearts), y0 + 3, 0xFFFF4D5E, false);
        }
        if (mode == Mode.OVER) {
            g.fill(x0, y0 + H / 2 - 22, x0 + W, y0 + H / 2 + 24, 0xC0000000);
            boolean win = ext() != null && ext().won();
            drawCentred(g, win ? "YOU WIN!" : "GAME OVER", x0 + W / 2, y0 + H / 2 - 14, win ? 0xFF5BFF6A : 0xFFFF4D5E);
            drawCentred(g, "SCORE " + score, x0 + W / 2, y0 + H / 2, 0xFFFFFFFF);
            if ((ticks / 10) % 2 == 0) {
                drawCentred(g, "SPACE: AGAIN   TAB: OTHER GAME", x0 + W / 2, y0 + H / 2 + 13, 0xFFAAAAAA);
            }
        }
        // the tube's scanlines
        for (int y = y0; y < y0 + H && !picture; y += 2) {
            g.fill(x0, y, x0 + W, y + 1, 0x22000000);
        }
    }

    /** The cabinet's control panel, under its screen: the stick leaning the way you push, the buttons going down. */
    private void drawPanel(GuiGraphics g, int x0, int y) {
        int in = inputBits();
        g.fill(x0 - 8, y, x0 + W + 8, y + 22, 0xFF2B1A48);
        g.fill(x0 - 8, y, x0 + W + 8, y + 1, 0xFFFF4FA8);
        g.fill(x0 - 8, y + 21, x0 + W + 8, y + 22, 0xFF3CD8F0);
        // the stick: a black plate, the shaft, a red ball pushed over
        int sx = x0 + 34, sy = y + 13;
        int dx = ((in & 2) != 0 ? 5 : 0) - ((in & 1) != 0 ? 5 : 0), dy = ((in & 8) != 0 ? 3 : 0) - ((in & 4) != 0 ? 3 : 0);
        g.fill(sx - 7, sy - 2, sx + 7, sy + 4, 0xFF101014);
        for (int i = 0; i <= 6; i++) { // (the shaft, from the plate to the ball)
            int px = sx + dx * i / 6, py = sy - 1 - (9 - dy) * i / 6;
            g.fill(px - 1, py - 1, px + 1, py + 1, 0xFFB8BCC8);
        }
        int bx = sx + dx, by = sy - 10 + dy;
        g.fill(bx - 4, by - 4, bx + 4, by + 4, 0xFFD02830);
        g.fill(bx - 3, by - 3, bx - 1, by - 1, 0xFFFF8A8A);
        // six buttons: the back row 1 2 and the rage key, the front row 3 4 and start
        int[] colours = {0xFF5BE05B, 0xFFD02830, 0xFF3050D8, 0xFFD02830, 0xFF3050D8, 0xFFF0C030};
        for (int i = 0; i < 6; i++) {
            boolean down = (in >> (4 + i) & 1) != 0;
            int cx = x0 + W - 74 + (i % 3) * 20 + (i < 3 ? 0 : 6), cy = y + (i < 3 ? 6 : 15);
            g.fill(cx - 6, cy - 4, cx + 6, cy + 4, 0xFF9098A8); // (its chrome ring)
            int c = colours[i];
            if (down) c = 0xFF000000 | ((c >> 16 & 255) * 3 / 5) << 16 | ((c >> 8 & 255) * 3 / 5) << 8 | (c & 255) * 3 / 5;
            g.fill(cx - 4, cy - 3 + (down ? 1 : 0), cx + 4, cy + 3, c);
            if (!down) g.fill(cx - 4, cy - 3, cx + 4, cy - 2, 0x60FFFFFF);
        }
    }

    private void drawTitle(GuiGraphics g, int x0, int y0, float partialTick) {
        float t = ticks + partialTick;
        drawCentred(g, NAMES[game], x0 + W / 2, y0 + 34, rainbow(t));
        if (ext() != null) {
            drawCentred(g, ext().help()[0], x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, ext().help()[1], x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        } else if (game == 2) {
            drawCentred(g, "Left / right: move  Up: turn", x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, "Down: faster  Space: drop", x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        } else if (game == 0) {
            drawCentred(g, "Arrows / WASD to steer", x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, "Eat, grow, don't bite yourself", x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        } else {
            drawCentred(g, "Mouse or arrows / A D: the paddle", x0 + W / 2, y0 + 60, 0xFFBBBBBB);
            drawCentred(g, "Space or click: launch", x0 + W / 2, y0 + 72, 0xFFBBBBBB);
        }
        if (((int) t / 10) % 2 == 0) {
            drawCentred(g, pad.connected ? "PRESS SPACE / START" : "PRESS SPACE", x0 + W / 2, y0 + 100, 0xFFFFFFFF);
        }
        drawCentred(g, pad.connected ? "C / X: CONTROLS" : "C: CONTROLS (KEYS & CONTROLLER)", x0 + W / 2, y0 + 88, 0xFF9AD8FF);
        drawCentred(g, "< TAB: " + NAMES[(game + 1) % NAMES.length] + " >", x0 + W / 2, y0 + 124, 0xFF7788AA);
        drawCentred(g, ArcadeSounds.muted() ? "M: SOUND OFF" : "M: SOUND ON", x0 + W / 2, y0 + 112, ArcadeSounds.muted() ? 0xFF886666 : 0xFF66AA77);
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
