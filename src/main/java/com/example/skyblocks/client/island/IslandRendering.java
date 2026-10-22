package com.example.skyblocks.client.island;

/**
 * What is being drawn right now, for block entity renderers that need to know (client only): inside a Sky Island
 * (a miniature copy: its "world position" is not where it is drawn), and inside an Island Painting's window (anything
 * drawn later in the frame would end up outside the window).
 */
public final class IslandRendering {
    public static boolean inIsland;
    public static boolean inWindow;
    /**
     * Collect cats for IslandCatBatch instead of queueing them for Shadered (a Sky Island on a pedestal while a shader
     * pack is active: they are drawn through client/iris/IrisIslandTarget).
     */
    public static boolean collectCats;
    /**
     * Draw cats right away, with the pose they are given (a Sky Island item: drawn in hand, in item frames... not in
     * the world's own pass).
     */
    public static boolean directCats;
    /**
     * Drawing into a mirror's picture (client/MirrorRenderer): the copied block entities inside Sky Islands are limited
     * to the ones drawn right away (cats, Minecraft's own), like the Sky Island item: others may queue their drawing for
     * later in the frame, where the mirror's copy would turn up in the real world.
     */
    public static boolean inMirror;

    private IslandRendering() {}
}
