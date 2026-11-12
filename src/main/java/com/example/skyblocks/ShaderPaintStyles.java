package com.example.skyblocks;

/**
 * What a painted pixel holds (ModComponents.ShaderPaint, painted at the infusion altar): one of Shadered's sky blocks'
 * skies, seen through one of its passes - 1 + sky * PASSES + pass (Shadered's SkyblockType and SkyblockPass, ours
 * included: client/ShaderPaintRender); 0, nothing.
 */
public final class ShaderPaintStyles {
    private ShaderPaintStyles() {}

    /** How many passes each sky has (SkyblockPass). */
    public static final int PASSES = 9;

    /** A pixel's value can be anything a byte holds (the client skips skies it hasn't got). */
    public static int count() {
        return 256;
    }

    public static int of(int sky, int pass) {
        return 1 + sky * PASSES + pass;
    }

    public static int sky(int style) {
        return (style - 1) / PASSES;
    }

    public static int pass(int style) {
        return (style - 1) % PASSES;
    }
}
