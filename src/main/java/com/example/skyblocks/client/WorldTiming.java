package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How long Shadered+'s heavy blocks take in the normal view (not in mirrors: MirrorTiming), on the graphics card (timer
 * queries, read a few frames later so nothing waits) and on the processor; shown on the F3 screen. Only timed while
 * the F3 screen is open.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WorldTiming {
    public static final int WORLD = 0, AQUARIUMS = 1, INFINITY = 2, DANCE_FLOORS = 3, KOI_PONDS = 4, SKY_BLOCKS = 5, MIRRORS = 6,
            HALF_RES = 7, COUNT = 8;
    private static final String[] NAMES = {"whole world", "aquariums", "infinity blocks", "dance floors", "koi ponds",
            "sky blocks", "mirrors (all)", "half-size pictures put on screen"};
    private static final int RING = 4;

    private static final class Frame {
        final List<int[]> spans = new ArrayList<>(); // {phase, startQuery, endQuery (-1: still open)}
        final List<Long> cpuStart = new ArrayList<>();
        final long[] cpu = new long[COUNT];
        boolean used;
    }

    private static final Frame[] FRAMES = new Frame[RING];
    private static final List<Integer> FREE = new ArrayList<>();
    private static int current = -1;
    private static boolean supported = true, on;
    private static final double[] gpuMs = new double[COUNT], cpuMs = new double[COUNT];
    private static int worldSpan = -1;
    private static long lastSeen;

    private WorldTiming() {}

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (MirrorRenderer.isRenderingReflection() || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass()) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            beginFrame();
            worldSpan = start(WORLD);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            stop(worldSpan);
            worldSpan = -1;
        }
    }

    private static void beginFrame() {
        on = supported && Minecraft.getInstance().getDebugOverlay().showDebugScreen();
        if (!on) {
            return;
        }
        try {
            current = (current + 1) % RING;
            Frame f = FRAMES[current];
            if (f == null) {
                f = FRAMES[current] = new Frame();
            }
            if (f.used) {
                boolean ready = true;
                for (int[] s : f.spans) {
                    if (s[2] >= 0 && GL15.glGetQueryObjecti(s[2], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                        ready = false; // (a part never stopped is left out)
                        break;
                    }
                }
                double[] gpu = new double[COUNT];
                for (int[] s : f.spans) {
                    if (ready && s[2] >= 0) {
                        long a = GL33.glGetQueryObjecti64(s[1], GL15.GL_QUERY_RESULT);
                        long b = GL33.glGetQueryObjecti64(s[2], GL15.GL_QUERY_RESULT);
                        gpu[s[0]] += (b - a) / 1.0e6;
                    }
                    FREE.add(s[1]);
                    if (s[2] >= 0) {
                        FREE.add(s[2]);
                    }
                }
                if (ready) {
                    for (int i = 0; i < COUNT; i++) {
                        gpuMs[i] = gpuMs[i] * 0.9 + gpu[i] * 0.1;
                        cpuMs[i] = cpuMs[i] * 0.9 + f.cpu[i] / 1.0e6 * 0.1;
                    }
                }
            }
            f.spans.clear();
            f.cpuStart.clear();
            java.util.Arrays.fill(f.cpu, 0);
            f.used = true;
            lastSeen = System.currentTimeMillis();
        } catch (RuntimeException | LinkageError e) {
            supported = false;
            on = false;
        }
    }

    /** Starts timing a part; returns what to pass to {@link #stop} (parts may overlap). Not in mirrors. */
    public static int start(int phase) {
        if (!on || current < 0 || FRAMES[current] == null || MirrorRenderer.isRenderingReflection()) {
            return -1;
        }
        try {
            int q = query();
            GL33.glQueryCounter(q, GL33.GL_TIMESTAMP);
            Frame f = FRAMES[current];
            f.spans.add(new int[] {phase, q, -1});
            f.cpuStart.add(System.nanoTime());
            return current * 100000 + f.spans.size() - 1;
        } catch (RuntimeException | LinkageError e) {
            supported = false;
            on = false;
            return -1;
        }
    }

    public static void stop(int token) {
        if (token < 0 || !supported) {
            return;
        }
        int frameIndex = token / 100000, i = token % 100000;
        Frame f = FRAMES[frameIndex];
        if (frameIndex != current || f == null || i >= f.spans.size() || f.spans.get(i)[2] >= 0) {
            return;
        }
        try {
            int q = query();
            GL33.glQueryCounter(q, GL33.GL_TIMESTAMP);
            int[] s = f.spans.get(i);
            s[2] = q;
            f.cpu[s[0]] += System.nanoTime() - f.cpuStart.get(i);
        } catch (RuntimeException | LinkageError e) {
            supported = false;
            on = false;
        }
    }

    private static int query() {
        return FREE.isEmpty() ? GL15.glGenQueries() : FREE.remove(FREE.size() - 1);
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen() || System.currentTimeMillis() - lastSeen > 2000) {
            return;
        }
        List<String> lines = event.getRight();
        lines.add("");
        lines.add(String.format(Locale.ROOT, "Shadered+ normal view: world GPU %.1f ms, CPU %.1f ms", gpuMs[WORLD], cpuMs[WORLD]));
        for (int i = 1; i < COUNT; i++) {
            if (gpuMs[i] >= 0.05 || cpuMs[i] >= 0.05) {
                lines.add(String.format(Locale.ROOT, "  %s: GPU %.2f ms, CPU %.2f ms", NAMES[i], gpuMs[i], cpuMs[i]));
            }
        }
    }
}
