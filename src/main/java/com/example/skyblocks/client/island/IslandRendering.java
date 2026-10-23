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
     * Where things are seen from, if not the camera (a mirror: the mirrored eye), for how much detail to draw.
     */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.world.phys.Vec3 viewer;
    /**
     * Drawing into a mirror (client/MirrorRenderer): Sky Islands, Island Paintings and the scanner's preview show only
     * their blocks (no chests, cats, sky blocks or mobs inside), which keeps mirrors cheap.
     */
    public static boolean inMirror;
    /** The Sky Island whose copied block entities are being drawn (null when none). */
    @org.jetbrains.annotations.Nullable
    public static IslandView island;

    private IslandRendering() {}
}
