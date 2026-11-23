package com.example.skyblocks;

/**
 * Written by tools/studio_spec.py: the Sky Studio's settings - its layers, each one's settings and where they are in
 * a studio sky's numbers (StudioSkies), and the looks to start from. The shader has the same list
 * (shaders/include/studio_params.glsl).
 */
public final class StudioSpec {
    /** How many numbers a studio sky has (each 0 .. 1), and in how many vec4s the shader gets them. */
    public static final int SIZE = 177, VECS = 45;

    /** How many numbers a studio sky had before the draw order (its ranks come after them). */
    public static final int SIZE_BEFORE_ORDER = 165;

    /** How many layers can be put in order (a rank: 0, 1/16, 2/16, ...). */
    public static final int ORDERED = 12;

    /** A setting: its name, kind ('o' the layer's switch, 'c' a colour: 3 numbers, 'n' a number), where it is. */
    public record Field(String id, char kind, int at) {}

    /** A layer: its name, where its switch is (-1: always on), where its rank in the draw order is (-1: always at the back), its settings. */
    public record Layer(String id, int on, int order, Field[] fields) {}

    public static final Layer[] LAYERS = {
            new Layer("global", -1, -1, new Field[] {new Field("brightness", 'n', 0), new Field("contrast", 'n', 1), new Field("saturation", 'n', 2), new Field("hue_shift", 'n', 3), new Field("speed", 'n', 4), new Field("spin", 'n', 5), new Field("vignette", 'n', 6)}),
            new Layer("gradient", 7, -1, new Field[] {new Field("top", 'c', 8), new Field("middle", 'c', 11), new Field("horizon", 'c', 14), new Field("ground", 'c', 17), new Field("horizon_height", 'n', 20), new Field("glow", 'n', 21), new Field("glow_width", 'n', 22), new Field("curve", 'n', 23)}),
            new Layer("nebula", 24, 165, new Field[] {new Field("colour1", 'c', 25), new Field("colour2", 'c', 28), new Field("colour3", 'c', 31), new Field("amount", 'n', 34), new Field("scale", 'n', 35), new Field("drift", 'n', 36), new Field("softness", 'n', 37), new Field("detail", 'n', 38), new Field("brightness", 'n', 39), new Field("swirl", 'n', 40)}),
            new Layer("stars", 41, 166, new Field[] {new Field("colour", 'c', 42), new Field("variety", 'n', 45), new Field("density", 'n', 46), new Field("size", 'n', 47), new Field("twinkle", 'n', 48), new Field("brightness", 'n', 49), new Field("milky_way", 'n', 50), new Field("band_tilt", 'n', 51), new Field("big_stars", 'n', 52)}),
            new Layer("aurora", 53, 170, new Field[] {new Field("colour1", 'c', 54), new Field("colour2", 'c', 57), new Field("brightness", 'n', 60), new Field("height", 'n', 61), new Field("thickness", 'n', 62), new Field("wave_speed", 'n', 63), new Field("waviness", 'n', 64), new Field("rays", 'n', 65), new Field("bands", 'n', 66)}),
            new Layer("moon", 67, 168, new Field[] {new Field("colour", 'c', 68), new Field("size", 'n', 71), new Field("height", 'n', 72), new Field("direction", 'n', 73), new Field("glow", 'n', 74), new Field("round", 'n', 75), new Field("craters", 'n', 76), new Field("rings", 'n', 77), new Field("ring_colour", 'c', 78), new Field("orbit", 'n', 81)}),
            new Layer("planet", 82, 167, new Field[] {new Field("colour1", 'c', 83), new Field("colour2", 'c', 86), new Field("size", 'n', 89), new Field("height", 'n', 90), new Field("direction", 'n', 91), new Field("bands", 'n', 92), new Field("rings", 'n', 93), new Field("ring_colour", 'c', 94), new Field("glow", 'n', 97), new Field("turn", 'n', 98)}),
            new Layer("clouds", 99, 171, new Field[] {new Field("colour", 'c', 100), new Field("shadow", 'c', 103), new Field("amount", 'n', 106), new Field("height", 'n', 107), new Field("scale", 'n', 108), new Field("speed", 'n', 109), new Field("softness", 'n', 110)}),
            new Layer("rays", 111, 169, new Field[] {new Field("colour", 'c', 112), new Field("strength", 'n', 115), new Field("count", 'n', 116), new Field("spin", 'n', 117), new Field("width", 'n', 118)}),
            new Layer("shooting", 119, 172, new Field[] {new Field("colour", 'c', 120), new Field("how_often", 'n', 123), new Field("trail", 'n', 124), new Field("speed", 'n', 125), new Field("size", 'n', 126)}),
            new Layer("mountains", 127, 175, new Field[] {new Field("colour", 'c', 128), new Field("far_colour", 'c', 131), new Field("height", 'n', 134), new Field("roughness", 'n', 135), new Field("layers", 'n', 136), new Field("haze", 'n', 137)}),
            new Layer("grid", 138, 174, new Field[] {new Field("colour", 'c', 139), new Field("spacing", 'n', 142), new Field("line_width", 'n', 143), new Field("scroll", 'n', 144), new Field("glow", 'n', 145), new Field("sun", 'n', 146), new Field("sun_colour", 'c', 147)}),
            new Layer("lake", 150, 176, new Field[] {new Field("tint", 'c', 151), new Field("clarity", 'n', 154), new Field("ripples", 'n', 155), new Field("ripple_speed", 'n', 156)}),
            new Layer("sparkles", 157, 173, new Field[] {new Field("colour", 'c', 158), new Field("amount", 'n', 161), new Field("size", 'n', 162), new Field("speed", 'n', 163), new Field("twinkle", 'n', 164)}),
    };

    public static final String[] PRESET_NAMES = {"dusk", "space", "neon", "dawn", "alien", "storm", "arctic", "inferno"};

    /** The looks to start from (a slot starts as one of them). */
    public static final float[][] PRESETS = {
            { // dusk
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.0706F, 0.051F,
                0.1804F, 0.2314F, 0.1216F, 0.3608F, 0.9412F, 0.5412F, 0.3647F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 1.0F, 0.4863F, 0.302F, 1.0F, 1.0F, 0.4353F,
                0.6824F, 0.3686F, 0.949F, 0.7608F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 1.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 1.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 1.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.35F, 0.12F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 0.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 1.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 0.0F, 0.1255F, 0.0784F,
                0.2275F, 0.2902F, 0.1843F, 0.4314F, 0.4F, 0.5F, 0.6F, 0.5F, 1.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                0.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // space
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.0078F, 0.0118F,
                0.0392F, 0.0431F, 0.0863F, 0.251F, 0.1059F, 0.2275F, 0.4196F, 0.0118F, 0.0196F, 0.0471F,
                0.55F, 0.2F, 0.4F, 0.5F, 1.0F, 0.2275F, 0.6275F, 1.0F, 0.6078F, 0.3608F,
                1.0F, 0.1686F, 0.8392F, 1.0F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 1.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.7F, 0.35F, 0.3F, 0.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 1.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.55F, 0.85F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 1.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 0.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 1.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 0.0F, 0.1255F, 0.0784F,
                0.2275F, 0.2902F, 0.1843F, 0.4314F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                0.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // neon
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.0392F, 0.0F,
                0.0941F, 0.1647F, 0.0431F, 0.302F, 1.0F, 0.2392F, 0.5059F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 0.0F, 0.4863F, 0.302F, 1.0F, 1.0F, 0.4353F,
                0.6824F, 0.3686F, 0.949F, 0.7608F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 1.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 1.0F, 0.7765F, 1.0F, 0.2392F, 0.1686F, 0.9412F, 1.0F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 0.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.35F, 0.12F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 0.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 1.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 1.0F, 0.0706F, 0.0F,
                0.1647F, 0.2392F, 0.0392F, 0.3686F, 0.4F, 0.5F, 0.6F, 0.5F, 1.0F, 0.1686F,
                0.9412F, 1.0F, 0.5F, 0.3F, 0.6F, 0.5F, 0.7F, 1.0F, 0.702F, 0.2784F,
                0.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // dawn
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.1137F, 0.1686F,
                0.3098F, 0.3569F, 0.4353F, 0.6588F, 1.0F, 0.7725F, 0.6196F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 1.0F, 1.0F, 0.8392F, 0.9098F, 0.6471F, 0.8471F,
                1.0F, 0.3686F, 0.949F, 0.7608F, 0.3F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 0.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 0.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 1.0F, 1.0F, 0.949F,
                0.7686F, 0.4F, 0.12F, 0.12F, 0.8F, 1.0F, 0.0F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 1.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 1.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 0.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 0.0F, 0.1255F, 0.0784F,
                0.2275F, 0.2902F, 0.1843F, 0.4314F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                1.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // alien
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.0235F, 0.0941F,
                0.0588F, 0.0784F, 0.3216F, 0.2118F, 0.7059F, 1.0F, 0.4157F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 1.0F, 0.2392F, 1.0F, 0.6902F, 0.8392F, 1.0F,
                0.2392F, 0.3686F, 0.949F, 0.7608F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 1.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 0.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 0.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.35F, 0.12F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 1.0F, 0.4784F, 0.8392F, 1.0F, 0.1647F, 0.3529F, 0.6196F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 0.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 0.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 1.0F, 0.0157F, 0.0784F,
                0.0471F, 0.0588F, 0.2392F, 0.1647F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                0.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 1.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // storm
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.102F, 0.1216F,
                0.1686F, 0.2314F, 0.2706F, 0.3451F, 0.5412F, 0.5843F, 0.6588F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 0.0F, 0.4863F, 0.302F, 1.0F, 1.0F, 0.4353F,
                0.6824F, 0.3686F, 0.949F, 0.7608F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 0.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 0.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 0.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.35F, 0.12F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 1.0F,
                0.6039F, 0.6471F, 0.7216F, 0.1647F, 0.1843F, 0.2275F, 0.8F, 0.3F, 0.5F, 0.3F,
                0.6F, 1.0F, 0.8745F, 0.9098F, 1.0F, 0.3F, 0.5F, 0.2F, 0.5F, 0.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 0.0F, 0.1255F, 0.0784F,
                0.2275F, 0.2902F, 0.1843F, 0.4314F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                1.0F, 0.1647F, 0.2039F, 0.2588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // arctic
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.0078F, 0.0314F,
                0.0784F, 0.0392F, 0.1412F, 0.251F, 0.1647F, 0.4157F, 0.5412F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 0.0F, 0.4863F, 0.302F, 1.0F, 1.0F, 0.4353F,
                0.6824F, 0.3686F, 0.949F, 0.7608F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 1.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 1.0F, 0.2392F, 1.0F, 0.6275F, 0.6275F, 0.4196F, 1.0F,
                0.9F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 0.0F, 0.9569F, 0.9373F,
                0.8824F, 0.4F, 0.35F, 0.12F, 0.3F, 0.0F, 0.5F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 0.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 0.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 1.0F, 0.0471F, 0.102F,
                0.1647F, 0.1647F, 0.2902F, 0.4F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                1.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 0.0F, 1.0F, 0.9451F,
                0.7216F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
            { // inferno
                0.5F, 0.5F, 0.5F, 0.0F, 0.5F, 0.0F, 0.0F, 1.0F, 0.102F, 0.0078F,
                0.0157F, 0.3529F, 0.0392F, 0.0314F, 1.0F, 0.4784F, 0.102F, 0.102F, 0.0627F, 0.1882F,
                0.55F, 0.7F, 0.4F, 0.5F, 1.0F, 1.0F, 0.2392F, 0.102F, 1.0F, 0.6902F,
                0.102F, 0.4157F, 0.0392F, 0.1647F, 0.6F, 0.5F, 0.25F, 0.8F, 0.5F, 0.6F,
                0.2F, 0.0F, 1.0F, 0.9686F, 0.902F, 0.3F, 0.45F, 0.3F, 0.65F, 0.8F,
                0.3F, 0.35F, 0.3F, 0.0F, 0.3686F, 0.949F, 0.7608F, 1.0F, 0.4353F, 0.6824F,
                0.75F, 0.35F, 0.4F, 0.4F, 0.5F, 0.4F, 0.6F, 1.0F, 1.0F, 0.3529F,
                0.102F, 0.4F, 0.35F, 0.12F, 0.3F, 1.0F, 0.2F, 0.0F, 0.8471F, 0.7804F,
                1.0F, 0.0F, 0.0F, 0.8784F, 0.6471F, 0.4157F, 0.5412F, 0.3098F, 0.2275F, 0.55F,
                0.45F, 0.62F, 0.5F, 0.6F, 0.8039F, 0.7255F, 0.6039F, 0.3F, 0.2F, 0.0F,
                0.9647F, 0.8235F, 0.902F, 0.3529F, 0.2392F, 0.4706F, 0.45F, 0.3F, 0.5F, 0.3F,
                0.6F, 1.0F, 1.0F, 0.851F, 0.6588F, 0.4F, 0.5F, 0.2F, 0.5F, 0.0F,
                1.0F, 1.0F, 1.0F, 0.6F, 0.55F, 0.5F, 0.4F, 1.0F, 0.0784F, 0.0078F,
                0.0078F, 0.2902F, 0.1843F, 0.4314F, 0.4F, 0.5F, 0.6F, 0.5F, 0.0F, 1.0F,
                0.3725F, 0.8235F, 0.5F, 0.3F, 0.6F, 0.5F, 0.0F, 1.0F, 0.702F, 0.2784F,
                0.0F, 0.2314F, 0.4353F, 0.6588F, 0.6F, 0.4F, 0.4F, 1.0F, 1.0F, 0.702F,
                0.2784F, 0.4F, 0.4F, 0.3F, 0.6F, 0.0F, 0.0625F, 0.125F, 0.1875F, 0.25F,
                0.3125F, 0.375F, 0.4375F, 0.5F, 0.5625F, 0.625F, 0.6875F
            },
    };

    private StudioSpec() {}
}
