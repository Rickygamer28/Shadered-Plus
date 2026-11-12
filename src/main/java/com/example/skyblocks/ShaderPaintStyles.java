package com.example.skyblocks;

/**
 * What a painted pixel holds (ModComponents.ShaderPaint, painted at the infusion altar): one of Shadered's sky blocks'
 * skies, seen through one of its passes - (sky + 1) << 4 | pass (Shadered's SkyblockType and SkyblockPass, ours
 * included: client/ShaderPaintRender); 0, nothing. Two bytes a pixel (room for all the skies there are).
 */
public final class ShaderPaintStyles {
    private ShaderPaintStyles() {}

    /** How many passes each sky has (SkyblockPass). */
    public static final int PASSES = 9;

    public static int of(int sky, int pass) {
        return (sky + 1) << 4 | pass;
    }

    public static int sky(int style) {
        return (style >> 4) - 1;
    }

    public static int pass(int style) {
        return style & 15;
    }
}
