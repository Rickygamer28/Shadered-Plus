package com.example.skyblocks.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;

/**
 * The mirrors' views (from the last frame), for Sodium's chunk search (mixin/sodium/SodiumViewportMixin): chunks a mirror
 * shows are kept in the main view's chunk list too, so its reflection has them (the reflection draws that list again).
 */
public final class MirrorCulling {
    /** A mirror's view: clip space from positions relative to the camera of {@link #camera}. */
    public record View(FrustumIntersection frustum) {}

    private static volatile List<View> views = List.of();
    /** Portals' other ends (relative to {@link #camera}): the sections on the line from the camera to each count as seen. */
    private static volatile List<Vec3> bridges = List.of();
    private static Vec3 camera = Vec3.ZERO;
    private static int lastCount, lastBridges;
    /** How far from the line a section's centre may be (more than half a section's diagonal: the sections touch face to face). */
    private static final double BRIDGE_RADIUS = 16.0;

    private MirrorCulling() {}

    /** (The render test: as before the bridges, to compare.) */
    public static boolean noBridges;
    private static boolean hooked;

    static void set(List<View> newViews, Vec3 cameraPosition, List<Vec3> portalEnds) {
        if (noBridges) {
            portalEnds = List.of();
        }
        views = List.copyOf(newViews);
        camera = cameraPosition;
        List<Vec3> rel = new java.util.ArrayList<>(portalEnds.size());
        for (Vec3 end : portalEnds) {
            rel.add(end.subtract(cameraPosition));
        }
        bridges = List.copyOf(rel);
        if (newViews.size() != lastCount || rel.size() != lastBridges) {
            lastCount = newViews.size();
            lastBridges = rel.size();
            scheduleTerrainUpdate(); // mirrors came into or out of view: search the chunks again
        }
    }

    /** Whether the chunk search may go every way, not only outwards from the camera (a portal in view: its other end). */
    public static boolean searchEveryWay() {
        return !bridges.isEmpty();
    }

    static void clear() {
        if (!views.isEmpty() || !bridges.isEmpty()) {
            views = List.of();
            bridges = List.of();
            lastCount = 0;
            lastBridges = 0;
        }
    }

    /** Whether a chunk section (its centre in block coordinates) is seen in one of the mirrors. */
    public static boolean visible(int centerX, int centerY, int centerZ) {
        if (!hooked) { // (the log says whether Sodium's chunk search is hooked at all)
            hooked = true;
            com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+: Sodium's chunk search asks about mirrors");
        }
        List<View> current = views;
        if ((current.isEmpty() && bridges.isEmpty()) || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return false;
        }
        float x = (float) (centerX - camera.x), y = (float) (centerY - camera.y), z = (float) (centerZ - camera.z);
        float r = 9.125F;
        for (View view : current) {
            if (view.frustum().testAab(x - r, y - r, z - r, x + r, y + r, z + r)) {
                return true;
            }
        }
        for (Vec3 end : bridges) { // (on the line from the camera to a portal's other end)
            double len2 = end.lengthSqr();
            double t = len2 < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, (x * end.x + y * end.y + z * end.z) / len2));
            double dx = x - end.x * t, dy = y - end.y * t, dz = z - end.z * t;
            if (dx * dx + dy * dy + dz * dz <= BRIDGE_RADIUS * BRIDGE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    static void scheduleTerrainUpdate() {
        try {
            Class<?> sodium = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
            Object renderer = sodium.getMethod("instanceNullable").invoke(null);
            if (renderer != null) {
                sodium.getMethod("scheduleTerrainUpdate").invoke(renderer);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // no Sodium: vanilla's chunk list is used as it is
        }
    }
}
