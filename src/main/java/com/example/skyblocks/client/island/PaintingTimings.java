package com.example.skyblocks.client.island;

import com.example.skyblocks.SkyBlocksMod;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Measures where Island Paintings spend their frame time, on the graphics card (timestamp queries, read back a few
 * frames later so nothing waits) and on the CPU, and writes the averages to the log every 10 seconds while paintings
 * are being drawn ("Shadered+ painting timings"). Off unless the game is started with -Dskyblocks.paintingTimings=true.
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PaintingTimings {
    public enum Phase { IRIS_SETUP, IRIS_DEPTH_COPY, WINDOW_MASK, ROOM, ISLAND, BLOCK_ENTITIES, CATS, BATCH_DRAW, SKY_BLOCKS, IRIS_RESTORE, COMPOSITE, PEDESTAL_CATS }

    private record Mark(Phase phase, int query, long cpu) {}

    private static List<Mark> frame = new ArrayList<>();
    private static final ArrayDeque<List<Mark>> PENDING = new ArrayDeque<>();
    private static final Map<Phase, double[]> TOTALS = new EnumMap<>(Phase.class); // {gpu ms, cpu ms}
    private static int frames, paintings, paintingsThisFrame;
    private static long windowStart = System.nanoTime();
    private static boolean broken = !Boolean.getBoolean("skyblocks.paintingTimings");

    private PaintingTimings() {}

    /** From now on, time counts towards {@code phase} (null: stop counting). */
    public static void mark(Phase phase) {
        if (broken) {
            return;
        }
        try {
            int query = GL15.glGenQueries();
            GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
            frame.add(new Mark(phase, query, System.nanoTime()));
        } catch (RuntimeException | LinkageError e) {
            broken = true;
        }
    }

    public static void stop() {
        mark(null);
    }

    public static void painting() {
        paintingsThisFrame++;
    }

    @SubscribeEvent
    public static void onFrameStart(TickEvent.RenderTickEvent event) {
        if (broken || event.phase != TickEvent.Phase.START) {
            return;
        }
        if (!frame.isEmpty()) {
            PENDING.add(frame);
            frame = new ArrayList<>();
            frames++;
            paintings += paintingsThisFrame;
        }
        paintingsThisFrame = 0;
        while (PENDING.size() > 12) {
            delete(PENDING.poll());
        }
        while (!PENDING.isEmpty()) {
            List<Mark> marks = PENDING.peek();
            Mark last = marks.get(marks.size() - 1);
            if (GL15.glGetQueryObjecti(last.query(), GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                break;
            }
            PENDING.poll();
            long previous = 0;
            for (int i = 0; i < marks.size(); i++) {
                long time = GL33.glGetQueryObjecti64(marks.get(i).query(), GL15.GL_QUERY_RESULT);
                if (i > 0 && marks.get(i - 1).phase() != null) {
                    double[] t = TOTALS.computeIfAbsent(marks.get(i - 1).phase(), k -> new double[2]);
                    t[0] += (time - previous) / 1.0E6;
                    t[1] += (marks.get(i).cpu() - marks.get(i - 1).cpu()) / 1.0E6;
                }
                previous = time;
            }
            delete(marks);
        }
        long now = System.nanoTime();
        if (now - windowStart > 10_000_000_000L) {
            if (frames > 0) {
                StringBuilder line = new StringBuilder("Shadered+ painting timings, average per frame over ").append(frames)
                        .append(" frames (").append(String.format("%.1f", paintings / (double) frames)).append(" paintings per frame):");
                double gpu = 0, cpu = 0;
                for (Map.Entry<Phase, double[]> e : TOTALS.entrySet()) {
                    double g = e.getValue()[0] / frames, c = e.getValue()[1] / frames;
                    gpu += g;
                    cpu += c;
                    line.append(String.format(" %s gpu %.2f ms cpu %.2f ms;", e.getKey().name().toLowerCase(), g, c));
                }
                line.append(String.format(" total gpu %.2f ms cpu %.2f ms", gpu, cpu));
                SkyBlocksMod.LOGGER.info(line.toString());
            }
            TOTALS.clear();
            frames = 0;
            paintings = 0;
            windowStart = now;
        }
    }

    private static void delete(List<Mark> marks) {
        for (Mark m : marks) {
            GL15.glDeleteQueries(m.query());
        }
    }
}
