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

    private IslandRendering() {}
}
