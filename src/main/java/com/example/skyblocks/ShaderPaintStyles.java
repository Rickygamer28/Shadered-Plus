package com.example.skyblocks;

/**
 * What a painted pixel holds (ModComponents.ShaderPaint, painted at the infusion altar): one of Shadered's sky blocks'
 * skies, seen through one of its passes, and how see-through it is - clear << 12 | (sky + 1) << 4 | pass (Shadered's
 * SkyblockType and SkyblockPass, ours included: client/ShaderPaintRender; clear 0: solid, as everything painted before
 * it could be, .. 15: barely there); 0, nothing. Two bytes a pixel (room for 255 skies).
 */
public final class ShaderPaintStyles {
    private ShaderPaintStyles() {}

    /** How many passes each sky has (SkyblockPass). */
    public static final int PASSES = 9;

    /** How see-through a style can be: 0 (solid) .. CLEAR_MAX. */
    public static final int CLEAR_MAX = 15;

    public static int of(int sky, int pass) {
        return (sky + 1) << 4 | pass;
    }

    public static int of(int sky, int pass, int clear) {
        return Math.max(0, Math.min(CLEAR_MAX, clear)) << 12 | of(sky, pass);
    }

    public static int sky(int style) {
        return ((style >> 4) & 255) - 1;
    }

    public static int pass(int style) {
        return style & 15;
    }

    /** How see-through it is: 0 solid .. CLEAR_MAX. */
    public static int clear(int style) {
        return (style >> 12) & 15;
    }

    /** How much of the sky shows (1: all of it, the item under it hidden). */
    public static float opacity(int style) {
        return 1.0F - clear(style) / 16.0F;
    }

    /** The same sky and look, solid. */
    public static int solid(int style) {
        return style & 0x0FFF;
    }
}
