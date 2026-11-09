package com.example.skyblocks.client.arcade;

import com.example.skyblocks.ArcadeBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * One of the arcade cabinet's games after the first three (client/ArcadeScreen): played on a field W x H, ticked 20
 * times a second; it draws itself, and says what colour (of the cabinet's palette) is at any point of it, for the
 * cabinet's own screen.
 */
public abstract class ArcadeGame {
    public static final int W = 192, H = 144;
    protected final RandomSource random = RandomSource.create();
    protected int score;
    protected boolean over, won;
    protected int ticks;

    public abstract String name();

    /** Two lines for its title screen: how to play. */
    public abstract String[] help();

    public abstract void start();

    public abstract void tick();

    public void keyPressed(int key) {}

    public void keyReleased(int key) {}

    /** A click on the field (its own coordinates). */
    public void mouseClicked(double x, double y, int button) {}

    public abstract void draw(GuiGraphics g, Font font, int x0, int y0, float partialTick);

    /** The palette colour at this point of the field. */
    public abstract int sample(float x, float y);

    /** Shown at the top right while playing (lives, the score of a match ...). */
    public String status() {
        return "";
    }

    public int score() {
        return score;
    }

    public boolean over() {
        return over;
    }

    public boolean won() {
        return won;
    }

    // ---- helpers ----

    public static int rgb(int palette) {
        return 0xFF000000 | ArcadeBlockEntity.PALETTE[palette & 15];
    }

    protected static void box(GuiGraphics g, int x0, int y0, float x, float y, float w, float h, int argb) {
        g.fill(x0 + Math.round(x), y0 + Math.round(y), x0 + Math.round(x + w), y0 + Math.round(y + h), argb);
    }

    protected static boolean left(int k) {
        return k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_A;
    }

    protected static boolean right(int k) {
        return k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_D;
    }

    protected static boolean up(int k) {
        return k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_W;
    }

    protected static boolean down(int k) {
        return k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_S;
    }

    protected static void centred(GuiGraphics g, Font font, String s, int x, int y, int colour) {
        g.drawString(font, s, x - font.width(s) / 2, y, colour, false);
    }
}
