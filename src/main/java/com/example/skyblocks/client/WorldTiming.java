package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How long the normal view (not mirrors: MirrorTiming) takes, on the graphics card (timer queries, read a few frames
 * later so nothing waits) and on the processor; shown on the F3 screen, only timed while it's open:
 * <ul>
 * <li>the whole world, and each stretch of it between the game's drawing stages (blocks, mobs, block entities,
 * see-through blocks, particles...);</li>
 * <li>Shadered's own passes (sky pictures, sky blocks, models) at each of its stages;</li>
 * <li>Shadered+'s heavy blocks one by one;</li>
 * <li>the processor time of block entities and mobs, by kind (their graphics card time is in their stages: the game
 * draws them all together).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorldTiming {
    public static final int WORLD = 0, AQUARIUMS = 1, INFINITY = 2, DANCE_FLOORS = 3, KOI_PONDS = 4, SKY_BLOCKS = 5, MIRRORS = 6,
            HALF_RES = 7, SHADERED_SKY = 8, SHADERED_BLOCK_ENTITIES = 9, SHADERED_END = 10;
    private static final int MAX = 48;
    private static final List<String> NAMES = new ArrayList<>(List.of("whole world", "aquariums", "infinity blocks", "dance floors",
            "koi ponds", "sky block pictures (ours)", "mirrors (all)", "half-size pictures put on screen",
            "Shadered passes: sky pictures", "Shadered passes: sky blocks, models", "Shadered passes: end of frame"));
    /** The stretches between drawing stages, by the stage that ends them (a phase each, made as they turn up). */
    private static final Map<Object, Integer> STAGES = new HashMap<>();
    private static final int FIRST_STAGE_LINE = 100; // (stage phases are listed in their own block)
    private static final int RING = 4;

    private static final class Frame {
        final List<int[]> spans = new ArrayList<>(); // {phase, startQuery, endQuery (-1: still open)}
        final List<Long> cpuStart = new ArrayList<>();
        final long[] cpu = new long[MAX];
        boolean used;
    }

    private static final Frame[] FRAMES = new Frame[RING];
    private static final List<Integer> FREE = new ArrayList<>();
    private static int current = -1;
    private static boolean supported = true, on;
    private static final double[] gpuMs = new double[MAX], cpuMs = new double[MAX];
    private static final boolean[] isStage = new boolean[MAX];
    private static int worldSpan = -1, stageSpan = -1;
    private static long lastSeen;
    /** Processor time per kind of block entity / mob in the normal view: this frame, and averaged {ms, how many}. */
    private static final Map<Class<?>, double[]> BE_NOW = new HashMap<>(), BE = new HashMap<>(), MOB_NOW = new HashMap<>(), MOB = new HashMap<>();
    private static final long[] beStart = new long[16], mobStart = new long[16];
    private static int beDepth, mobDepth, shaderedDepth, shaderedSpan = -1;

    private WorldTiming() {}

    private static boolean skip() {
        return MirrorRenderer.isRenderingReflection() || com.example.skyblocks.client.iris.IrisCompat.renderingShadowPass();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStageFirst(RenderLevelStageEvent event) {
        if (skip()) {
            return;
        }
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            beginFrame();
            worldSpan = start(WORLD);
            stageSpan = -1;
        } else if (stageSpan >= 0) {
            // the stretch since the last stage, named for what the game drew in it
            retarget(stageSpan, stagePhase(stage));
            stop(stageSpan);
            stageSpan = -1;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            stageSpan = start(WORLD); // (its phase set when it ends)
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStageLast(RenderLevelStageEvent event) {
        if (!skip() && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            stop(worldSpan);
            worldSpan = -1;
        }
    }

    private static int stagePhase(RenderLevelStageEvent.Stage stage) {
        Integer known = STAGES.get(stage);
        if (known != null) {
            return known;
        }
        String name;
        if (stage == RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            name = "solid blocks (and Shadered's sky pictures)";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            name = "cutout blocks";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            name = "mobs (and mirrors, aquariums, infinity blocks)";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            name = "block entities";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            name = "see-through blocks (and Shadered's sky blocks, models)";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            name = "tripwire";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            name = "particles";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            name = "clouds, rain, snow";
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            name = "the rest (end of the world)";
        } else {
            String s = String.valueOf(stage);
            name = s.contains("mipped") || s.contains("MIPPED") ? "leaves, grass (cutout mipped blocks)" : "up to " + s;
        }
        if (NAMES.size() >= MAX) {
            return WORLD;
        }
        NAMES.add(name);
        int phase = NAMES.size() - 1;
        isStage[phase] = true;
        STAGES.put(stage, phase);
        return phase;
    }

    // ---- Shadered's passes (ComplexPassRendererMixin) ----

    public static void shaderedStart(Object stage) {
        if (shaderedDepth++ == 0 && !skip()) {
            String s = String.valueOf(stage);
            shaderedSpan = start(s.contains("SKY") ? SHADERED_SKY : s.contains("BLOCK") ? SHADERED_BLOCK_ENTITIES : SHADERED_END);
        }
    }

    public static void shaderedEnd() {
        if (shaderedDepth > 0 && --shaderedDepth == 0) {
            stop(shaderedSpan);
            shaderedSpan = -1;
        }
    }

    // ---- block entities and mobs, by kind (TimingMixins) ----

    public static void blockEntityStart() {
        if (beDepth < beStart.length) {
            beStart[beDepth] = on ? System.nanoTime() : 0L;
        }
        beDepth++;
    }

    public static void blockEntityEnd(Object be) {
        if (beDepth <= 0) {
            return;
        }
        beDepth--;
        if (beDepth < beStart.length && beStart[beDepth] != 0L && be != null && !MirrorRenderer.isRenderingReflection()) {
            double[] k = BE_NOW.computeIfAbsent(be.getClass(), c -> new double[2]);
            k[0] += (System.nanoTime() - beStart[beDepth]) / 1.0e6;
            k[1]++;
        }
    }

    public static void mobStart() {
        if (mobDepth < mobStart.length) {
            mobStart[mobDepth] = on ? System.nanoTime() : 0L;
        }
        mobDepth++;
    }

    public static void mobEnd(Object entity) {
        if (mobDepth <= 0) {
            return;
        }
        mobDepth--;
        if (mobDepth < mobStart.length && mobStart[mobDepth] != 0L && entity != null && !MirrorRenderer.isRenderingReflection()) {
            double[] k = MOB_NOW.computeIfAbsent(entity.getClass(), c -> new double[2]);
            k[0] += (System.nanoTime() - mobStart[mobDepth]) / 1.0e6;
            k[1]++;
        }
    }

    private static void average(Map<Class<?>, double[]> now, Map<Class<?>, double[]> avg) {
        for (double[] k : avg.values()) {
            k[0] *= 0.9;
            k[1] *= 0.9;
        }
        for (Map.Entry<Class<?>, double[]> e : now.entrySet()) {
            double[] k = avg.computeIfAbsent(e.getKey(), x -> new double[2]);
            k[0] += e.getValue()[0] * 0.1;
            k[1] += e.getValue()[1] * 0.1;
        }
        now.clear();
        avg.values().removeIf(k -> k[0] < 0.001 && k[1] < 0.01);
    }

    // ---- frames and spans ----

    private static void beginFrame() {
        beDepth = 0;
        mobDepth = 0;
        shaderedDepth = 0;
        on = supported && Minecraft.getInstance().options.renderDebug;
        if (!on) {
            BE_NOW.clear();
            MOB_NOW.clear();
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
                double[] gpu = new double[MAX];
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
                    for (int i = 0; i < MAX; i++) {
                        gpuMs[i] = gpuMs[i] * 0.9 + gpu[i] * 0.1;
                        cpuMs[i] = cpuMs[i] * 0.9 + f.cpu[i] / 1.0e6 * 0.1;
                    }
                }
            }
            average(BE_NOW, BE);
            average(MOB_NOW, MOB);
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

    private static void retarget(int token, int phase) {
        if (token < 0) {
            return;
        }
        int frameIndex = token / 100000, i = token % 100000;
        Frame f = FRAMES[frameIndex];
        if (frameIndex == current && f != null && i < f.spans.size()) {
            f.spans.get(i)[0] = phase;
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
    public static void onWorldDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().options.renderDebug || System.currentTimeMillis() - lastSeen > 2000) {
            return;
        }
        List<String> lines = event.getLeft();
        lines.add("");
        lines.add(String.format(Locale.ROOT, "Normal view: world GPU %.1f ms, CPU %.1f ms. By stage:", gpuMs[WORLD], cpuMs[WORLD]));
        for (int i = 1; i < NAMES.size(); i++) {
            if (isStage[i] && (gpuMs[i] >= 0.05 || cpuMs[i] >= 0.05)) {
                lines.add(String.format(Locale.ROOT, "  %s: GPU %.2f, CPU %.2f", NAMES.get(i), gpuMs[i], cpuMs[i]));
            }
        }
        lines.add("Parts of those (GPU, CPU ms):");
        for (int i = 1; i < NAMES.size(); i++) {
            if (!isStage[i] && (gpuMs[i] >= 0.05 || cpuMs[i] >= 0.05)) {
                lines.add(String.format(Locale.ROOT, "  %s: %.2f, %.2f", NAMES.get(i), gpuMs[i], cpuMs[i]));
            }
        }
        lines.add(SkyUsage.debugLine());
        kinds(lines, "Block entities (CPU)", BE);
        kinds(lines, "Mobs (CPU)", MOB);
    }

    private static void kinds(List<String> lines, String title, Map<Class<?>, double[]> avg) {
        List<Map.Entry<Class<?>, double[]>> top = avg.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).limit(6).toList();
        if (top.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder(title).append(':');
        lines.add(sb.toString());
        for (Map.Entry<Class<?>, double[]> e : top) {
            lines.add(String.format(Locale.ROOT, "  %s x%.0f: %.2f ms", e.getKey().getSimpleName(), e.getValue()[1], e.getValue()[0]));
        }
    }
}
