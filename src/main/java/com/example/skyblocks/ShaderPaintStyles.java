package com.example.skyblocks;

/**
 * The shaders you can paint onto an item at the infusion altar (ModComponents.ShaderPaint). Index = the style's number
 * in a painted pixel (0: none). From LIVE on, the real thing: one of Shadered+'s shader blocks drawing itself, live
 * (client/ShaderCapture: each effect block effect, and the blocks that are their shader - kaleidoscope, black hole,
 * plasma ball...); before that, shader paint's own takes on them (shaders/core/shader_paint).
 */
public final class ShaderPaintStyles {
    private ShaderPaintStyles() {}

    /** Names (lang: shader_paint.skyblocks.<name>), by number; 0 is no shader. */
    public static final String[] NAMES = {
            "none", "galaxy", "lava_lamp", "plasma", "black_hole", "aurora", "hologram", "kaleidoscope", "neon", "magma", "koi_pond",
            "infinity", "disco", "synthwave", "whirlpool", "fire", "holo_foil", "frost", "light_bridge", "geode", "waterfall",
            "effect_swirl", "effect_wave", "effect_cycle", "effect_breathe", "effect_strobe", "effect_sparkle", "effect_plasma",
            "effect_scanner", "effect_ripples", "effect_gradient", "effect_aurora", "effect_music", "effect_ink", "effect_fire",
            "effect_rain", "effect_lava", "effect_warp", "effect_spectrum", "kaleidoscope_block", "black_hole_block", "plasma_ball_block",
            "lava_lamp_block", "lava_pillar", "fire_tornado", "jellyfish_tank", "singularity", "weather_globe", "planetarium", "disco_ball"};

    /** The first live style (a real shader block's own shader). */
    public static final int LIVE = 21;
    /** The live effect block effects (LIVE + the effect's number), then the blocks. */
    public static final int EFFECTS = 18;

    public static int count() {
        return NAMES.length;
    }

    /** The palette's order: the real shaders first, then shader paint's own. */
    public static int[] order() {
        int[] o = new int[NAMES.length - 1];
        int k = 0;
        for (int i = LIVE; i < NAMES.length; i++) {
            o[k++] = i;
        }
        for (int i = 1; i < LIVE; i++) {
            o[k++] = i;
        }
        return o;
    }
}
