package com.example.skyblocks;

/**
 * The shaders you can paint onto an item at the infusion altar (ModComponents.ShaderPaint), each the look of one of
 * Shadered+'s shader blocks (drawn by shaders/core/shader_paint). Index = the style's number in a painted pixel (0: none).
 */
public final class ShaderPaintStyles {
    private ShaderPaintStyles() {}

    /** Names (lang: shader_paint.skyblocks.<name>), by number; 0 is no shader. */
    public static final String[] NAMES = {"none", "galaxy", "lava_lamp", "plasma", "black_hole", "aurora", "hologram", "kaleidoscope", "neon",
            "magma", "koi_pond", "infinity", "disco", "synthwave", "whirlpool", "fire", "holo_foil", "frost", "light_bridge", "geode", "waterfall"};

    public static int count() {
        return NAMES.length;
    }
}
