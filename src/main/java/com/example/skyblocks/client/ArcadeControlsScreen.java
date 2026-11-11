package com.example.skyblocks.client;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The arcade's controls, game by game: each thing you do in it with two keys and two controller buttons. Click one,
 * then press the key (or the controller button) you want for it; Backspace clears it, Esc keeps it as it was.
 */
public class ArcadeControlsScreen extends Screen {
    private static final int NAME_W = 84, CELL_W = 64;
    /** A row's height: 20, or less when the screen's too short for all of a game's rows. */
    private int ROW = 20;
    private final Screen parent;
    private int game;
    /** What's waiting for a key or button: the action, and the column (0, 1 keys; 2, 3 controller); -1 nothing. */
    private int listenAction = -1, listenCol = -1;
    private final ArcadeControls.Pad pad = new ArcadeControls.Pad();
    private int top;

    public ArcadeControlsScreen(Screen parent, int game) {
        super(Component.literal("Arcade controls"));
        this.parent = parent;
        this.game = game;
    }

    private static final String[] TITLES = {"SNAKE", "BREAKOUT", "TETRIS", "WHISKER MAZE", "PONG", "FROGGER", "MINESWEEPER", "TIKKIN"};

    @Override
    protected void init() {
        ArcadeControls.Action[] acts = ArcadeControls.GAMES[game];
        int w = NAME_W + CELL_W * 4;
        int left = width / 2 - w / 2;
        ROW = Mth.clamp((height - 34 - 50) / Math.max(1, acts.length), 14, 20);
        top = Math.max(34, height / 2 - (acts.length * ROW) / 2 - 20);
        for (int a = 0; a < acts.length; a++) {
            for (int c = 0; c < 4; c++) {
                final int action = a, col = c;
                boolean listening = listenAction == a && listenCol == c;
                String label = listening ? "> ? <" : c < 2 ? ArcadeControls.keyName(ArcadeControls.key(game, a, c))
                        : ArcadeControls.padName(ArcadeControls.pad(game, a, c - 2));
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    listenAction = action;
                    listenCol = col;
                    rebuildWidgets();
                }).bounds(left + NAME_W + c * CELL_W, top + a * ROW, CELL_W - 2, ROW - 2).build());
            }
        }
        int by = top + acts.length * ROW + 8;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> switchGame(-1)).bounds(width / 2 - 154, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset this game"), b -> {
            ArcadeControls.resetGame(game);
            rebuildWidgets();
        }).bounds(width / 2 - 130, by, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 6, by, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> switchGame(1)).bounds(width / 2 + 118, by, 20, 20).build());
    }

    private void switchGame(int d) {
        game = Math.floorMod(game + d, ArcadeControls.GAMES.length);
        listenAction = listenCol = -1;
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (listenAction >= 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // (kept as it was)
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                if (listenCol < 2) ArcadeControls.setKey(game, listenAction, listenCol, ArcadeControls.NONE);
                else ArcadeControls.setPad(game, listenAction, listenCol - 2, ArcadeControls.NONE);
            } else if (listenCol < 2) {
                ArcadeControls.setKey(game, listenAction, listenCol, key);
            } else {
                return true; // (waiting for a controller button)
            }
            listenAction = listenCol = -1;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<Integer> pressed = new ArrayList<>(), released = new ArrayList<>();
        pad.poll(pressed, released);
        for (int input : pressed) {
            if (listenAction >= 0 && listenCol >= 2) {
                ArcadeControls.setPad(game, listenAction, listenCol - 2, input);
                listenAction = listenCol = -1;
                rebuildWidgets();
            } else if (listenAction < 0) {
                if (input == ArcadeControls.B || input == ArcadeControls.BACK) onClose();
                else if (input == ArcadeControls.LB) switchGame(-1);
                else if (input == ArcadeControls.RB) switchGame(1);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        ArcadeControls.Action[] acts = ArcadeControls.GAMES[game];
        int w = NAME_W + CELL_W * 4;
        int left = width / 2 - w / 2;
        g.drawCenteredString(font, TITLES[game] + " - CONTROLS", width / 2, top - 30, 0xFFFFE74A);
        String[] heads = {"Key", "Other key", "Controller", "Other button"};
        for (int c = 0; c < 4; c++) {
            g.drawCenteredString(font, heads[c], left + NAME_W + c * CELL_W + CELL_W / 2 - 1, top - 12, 0xFFA0A0A0);
        }
        for (int a = 0; a < acts.length; a++) {
            g.drawString(font, acts[a].name(), left, top + a * ROW + (ROW - 8) / 2, 0xFFFFFFFF);
        }
        String hint = listenAction >= 0 ? (listenCol < 2 ? "Press a key - Backspace: none, Esc: keep it" : "Press a controller button - Backspace: none, Esc: keep it")
                : pad.connected ? "Controller connected - LB / RB: other games, B: back" : "Click one to change it";
        g.drawCenteredString(font, hint, width / 2, top + acts.length * ROW + 34, listenAction >= 0 ? 0xFF3AE8FF : 0xFF909090);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
