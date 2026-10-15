package com.example.skyblocks.client;

import com.noodlegamer76.shadered.client.util.skyblock.SkyblockBatchData;
import org.jetbrains.annotations.Nullable;

/**
 * Which Shadered skies are actually in use, so the ones nobody is looking at aren't drawn every frame.
 *
 * Shadered draws the picture of every sky (its own ten and our six) into its own screen-sized texture every frame,
 * whether or not a block, liquid or item of that sky is anywhere in sight: sixteen full-screen passes, one of them
 * the whole vanilla sky with clouds again. A sky is marked as used whenever something queues a block of it
 * (SkyblockBatchData.add), asks for its texture (render types, Sky Liquids) or looks up its batch; its picture is then
 * kept up to date for a few seconds after the last use, and not drawn at all while unused (see SkyPassMixin).
 */
public final class SkyUsage {
    /** How long a sky keeps being drawn after it was last used (so looking around doesn't stop and start it). */
    private static final long KEEP_NANOS = 3_000_000_000L;

    /** Added to SkyblockBatchData by SkyblockBatchDataMixin. */
    public interface Tracked {
        long skyblocks$lastUsed();

        void skyblocks$markUsed();
    }

    public static void mark(@Nullable SkyblockBatchData data) {
        if (data instanceof Tracked tracked) {
            tracked.skyblocks$markUsed();
        }
    }

    /** Whether the sky with this batch was used recently (always true if the tracking isn't there). */
    public static boolean recentlyUsed(@Nullable SkyblockBatchData data) {
        if (!(data instanceof Tracked tracked)) {
            return true;
        }
        long last = tracked.skyblocks$lastUsed();
        return last != 0 && System.nanoTime() - last < KEEP_NANOS;
    }

    /** Logs whether the tracking mixins are in place (once, at client setup). */
    public static void report() {
        boolean batch = new SkyblockBatchData() instanceof Tracked;
        boolean passes = false;
        for (java.lang.reflect.Method m : com.noodlegamer76.shadered.client.renderer.complexpasses.SkyboxRenderPass.class.getDeclaredMethods()) {
            if (m.getName().contains("skyblocks$skipUnused")) {
                passes = true;
            }
        }
        com.example.skyblocks.SkyBlocksMod.LOGGER.info("Shadered+: sky usage tracking: batches {}, sky passes {}",
                batch ? "tracked" : "NOT tracked", passes ? "skipped while unused" : "NOT skipped");
    }

    private SkyUsage() {}
}
