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
    private static Vec3 camera = Vec3.ZERO;
    private static int lastCount;

    private MirrorCulling() {}

    static void set(List<View> newViews, Vec3 cameraPosition) {
        views = List.copyOf(newViews);
        camera = cameraPosition;
        if (newViews.size() != lastCount) {
            lastCount = newViews.size();
            scheduleTerrainUpdate(); // mirrors came into or out of view: search the chunks again
        }
    }

    static void clear() {
        if (!views.isEmpty()) {
            views = List.of();
            lastCount = 0;
        }
    }

    /** Whether a chunk section (its centre in block coordinates) is seen in one of the mirrors. */
    public static boolean visible(int centerX, int centerY, int centerZ) {
        List<View> current = views;
        if (current.isEmpty() || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return false;
        }
        float x = (float) (centerX - camera.x), y = (float) (centerY - camera.y), z = (float) (centerZ - camera.z);
        float r = 9.125F;
        for (View view : current) {
            if (view.frustum().testAab(x - r, y - r, z - r, x + r, y + r, z + r)) {
                return true;
            }
        }
        return false;
    }

    private static void scheduleTerrainUpdate() {
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
