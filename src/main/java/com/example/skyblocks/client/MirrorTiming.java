package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How long mirrors take, per part, on the graphics card (timer queries, read a few frames later so nothing waits) and
 * on the processor: shown on the F3 screen while mirrors are drawn, to see what costs the frames.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MirrorTiming {
    public static final int SKY = 0, BLOCKS = 1, MOBS = 2, BLOCK_ENTITIES = 3, SHADERED_MODELS = 4, SHADERED_SKIES = 5,
            INSIDE = 6, SEE_THROUGH = 7, FACES = 8, PORTALS = 9, INFINITY = 10, DANCE_FLOORS = 11, AQUARIUMS = 12, COUNT = 13;
    private static final String[] NAMES = {"sky", "blocks", "mobs", "block entities", "shadered models", "shadered skies",
            "mirrors inside", "see-through", "faces", "portal skies", "infinity blocks", "dance floors", "aquariums"};
    private static final int RING = 4;

    private static final class Frame {
        final List<int[]> spans = new ArrayList<>(); // {phase, startQuery, endQuery}
        final long[] cpu = new long[COUNT];
        int passes, pictures;
        boolean used;
    }

    private static final Frame[] FRAMES = new Frame[RING];
    private static final List<Integer> FREE = new ArrayList<>();
    private static int current = -1;
    private static boolean supported = true;
    private static final double[] gpuMs = new double[COUNT], cpuMs = new double[COUNT];
    private static double passes, pictures;
    private static long lastSeen;
    private static int openPhase = -1, openQuery;
    /** Processor time per kind of block entity drawn in mirrors (this frame, and averaged): ms and how many. */
    private static final java.util.Map<String, double[]> KINDS_NOW = new java.util.HashMap<>();
    private static final java.util.Map<String, double[]> KINDS = new java.util.HashMap<>();

    public static void blockEntity(String kind, long nanos) {
        double[] k = KINDS_NOW.computeIfAbsent(kind, x -> new double[2]);
        k[0] += nanos / 1.0e6;
        k[1]++;
    }
    private static long openCpu;

    private MirrorTiming() {}

    /** Start of a frame's mirrors: collect a frame from a few frames ago (its results are ready by now). */
    public static void beginFrame() {
        if (!supported) {
            return;
        }
        try {
            current = (current + 1) % RING;
            Frame f = FRAMES[current];
            if (f == null) {
                f = FRAMES[current] = new Frame();
            }
            if (f.used) {
                double[] gpu = new double[COUNT];
                boolean ready = true;
                for (int[] s : f.spans) {
                    if (GL15.glGetQueryObjecti(s[2], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                        ready = false;
                        break;
                    }
                }
                for (int[] s : f.spans) {
                    if (ready) {
                        long a = GL33.glGetQueryObjecti64(s[1], GL15.GL_QUERY_RESULT);
                        long b = GL33.glGetQueryObjecti64(s[2], GL15.GL_QUERY_RESULT);
                        gpu[s[0]] += (b - a) / 1.0e6;
                    }
                    FREE.add(s[1]);
                    FREE.add(s[2]);
                }
                if (ready) {
                    for (int i = 0; i < COUNT; i++) {
                        gpuMs[i] = gpuMs[i] * 0.9 + gpu[i] * 0.1;
                        cpuMs[i] = cpuMs[i] * 0.9 + f.cpu[i] / 1.0e6 * 0.1;
                    }
                    passes = passes * 0.9 + f.passes * 0.1;
                    pictures = pictures * 0.9 + f.pictures * 0.1;
                }
            }
            for (double[] k : KINDS.values()) {
                k[0] *= 0.9;
                k[1] *= 0.9;
            }
            for (java.util.Map.Entry<String, double[]> e : KINDS_NOW.entrySet()) {
                double[] k = KINDS.computeIfAbsent(e.getKey(), x -> new double[2]);
                k[0] += e.getValue()[0] * 0.1;
                k[1] += e.getValue()[1] * 0.1;
            }
            KINDS_NOW.clear();
            KINDS.values().removeIf(k -> k[0] < 0.001 && k[1] < 0.01);
            f.spans.clear();
            java.util.Arrays.fill(f.cpu, 0);
            f.passes = 0;
            f.pictures = 0;
            f.used = true;
            lastSeen = System.currentTimeMillis();
        } catch (RuntimeException | LinkageError e) {
            supported = false;
        }
    }

    public static void counts(int passCount, int pictureCount) {
        if (current >= 0 && FRAMES[current] != null) {
            FRAMES[current].passes += passCount;
            FRAMES[current].pictures = pictureCount;
        }
    }

    /** Starts timing a part (ends the part being timed, if any). */
    public static void start(int phase) {
        end();
        if (!supported || current < 0 || FRAMES[current] == null) {
            return;
        }
        try {
            openQuery = query();
            GL33.glQueryCounter(openQuery, GL33.GL_TIMESTAMP);
            openPhase = phase;
            openCpu = System.nanoTime();
        } catch (RuntimeException | LinkageError e) {
            supported = false;
        }
    }

    /** Ends the part being timed. */
    public static void end() {
        if (openPhase < 0) {
            return;
        }
        try {
            int stop = query();
            GL33.glQueryCounter(stop, GL33.GL_TIMESTAMP);
            Frame f = FRAMES[current];
            f.spans.add(new int[] {openPhase, openQuery, stop});
            f.cpu[openPhase] += System.nanoTime() - openCpu;
        } catch (RuntimeException | LinkageError e) {
            supported = false;
        }
        openPhase = -1;
    }

    private static int query() {
        return FREE.isEmpty() ? GL15.glGenQueries() : FREE.remove(FREE.size() - 1);
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen() || System.currentTimeMillis() - lastSeen > 2000) {
            return;
        }
        double gpu = 0, cpu = 0;
        for (int i = 0; i < COUNT; i++) {
            gpu += gpuMs[i];
            cpu += cpuMs[i];
        }
        List<String> lines = event.getRight();
        lines.add("");
        lines.add(String.format(Locale.ROOT, "Mirrors: %.1f pictures drawn of %.0f, GPU %.1f ms, CPU %.1f ms", passes, pictures, gpu, cpu));
        for (int i = 0; i < COUNT; i++) {
            if (gpuMs[i] >= 0.05 || cpuMs[i] >= 0.05) {
                lines.add(String.format(Locale.ROOT, "  %s: GPU %.2f ms, CPU %.2f ms", NAMES[i], gpuMs[i], cpuMs[i]));
            }
        }
        KINDS.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).limit(5)
                .forEach(e -> lines.add(String.format(Locale.ROOT, "    %s x%.0f: CPU %.2f ms", e.getKey(), e.getValue()[1], e.getValue()[0])));
    }
}
