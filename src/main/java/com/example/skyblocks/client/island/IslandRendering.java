package com.example.skyblocks.client.island;

/**
 * What is being drawn right now, for block entity renderers that need to know (client only): inside a Sky Island
 * (a miniature copy: its "world position" is not where it is drawn), and inside an Island Painting's window (anything
 * drawn later in the frame would end up outside the window).
 */
public final class IslandRendering {
    public static boolean inIsland;
    public static boolean inWindow;

    private IslandRendering() {}
}
