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

    /** The cabinet's sounds (assets/skyblocks/sounds/arcade, made by tools/make_arcade_sounds.py), and how loud each. */
    public enum Sfx {
        BLIP(0.35F), SELECT(0.5F), START(0.6F), COIN(0.6F), GAMEOVER(0.7F), WIN(0.7F), LEVELUP(0.6F),
        SNAKE_EAT(0.5F), SNAKE_DIE(0.7F),
        PADDLE(0.45F), WALL(0.3F), BRICK(0.45F), LOSE(0.6F),
        MOVE(0.2F), ROTATE(0.3F), LAND(0.5F), LINE(0.6F), FOUR(0.7F), DROP(0.5F),
        CHOMP_A(0.35F), CHOMP_B(0.35F), POWER(0.55F), CATCH(0.6F), DEATH(0.7F), BONUS(0.6F), MAZE_START(0.55F),
        PONG_HIT(0.45F), PONG_WALL(0.35F), PONG_SCORE(0.55F), PONG_LOSE(0.55F),
        HOP(0.35F), SPLASH(0.6F), SQUASH(0.6F), HOME(0.6F),
        REVEAL(0.35F), FLOOD(0.45F), FLAG(0.4F), BOOM(0.9F),
        HIT_LIGHT(0.55F), HIT_HEAVY(0.75F), BLOCK(0.45F), WHOOSH(0.25F), COUNTER(0.5F), LAUNCH(0.6F), SCREW(0.5F),
        WALL_SPLAT(0.8F), THROW(0.7F), KO(1.0F), ROUND(0.7F), FIGHT(0.6F), RAGE(0.6F), CRUSH(0.6F), KI(0.5F),
        PARRY(0.6F), THUD(0.45F), ELECTRIC(0.55F), STEP(0.2F);

        public final float volume;

        Sfx(float volume) {
            this.volume = volume;
        }

        /** Its sound's name: arcade.<name> in skyblocks' sounds.json. */
        public String file() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** A sound it wants played: which, at what pitch, how loud (times its own). */
    public record SoundCall(Sfx sfx, float pitch, float volume) {}

    private final java.util.List<SoundCall> sounds = new java.util.ArrayList<>();

    protected void sfx(Sfx s) {
        sfx(s, 1.0F, 1.0F);
    }

    protected void sfx(Sfx s, float pitch) {
        sfx(s, pitch, 1.0F);
    }

    protected void sfx(Sfx s, float pitch, float volume) {
        if (sounds.size() < 32) sounds.add(new SoundCall(s, pitch, volume));
    }

    /** The sounds it's asked for since last time (the screen plays them). */
    public java.util.List<SoundCall> drainSounds() {
        if (sounds.isEmpty()) return java.util.List.of();
        java.util.List<SoundCall> out = new java.util.ArrayList<>(sounds);
        sounds.clear();
        return out;
    }

    public abstract String name();

    /** Two lines for its title screen: how to play. */
    public abstract String[] help();

    public abstract void start();

    public abstract void tick();

    /** The music it wants playing now (a looping track: arcade.<name>), or none. */
    public String music() {
        return null;
    }

    /** How many times a second it ticks: 20 (with the game), or more - then the screen runs it on its own clock. */
    public int rate() {
        return 20;
    }

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
