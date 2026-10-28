package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/**
 * Temporary checks for the 1.20.1 port: what the new blocks' renderers get to do, written to the log every few
 * seconds while in a world ("Shadered+ check:").
 */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Diag {
    private static final Map<String, Long> COUNTS = new TreeMap<>();
    private static final Map<String, String> NOTES = new TreeMap<>();
    private static long last;

    private Diag() {}

    public static void count(String key) {
        COUNTS.merge(key, 1L, Long::sum);
    }

    public static void note(String key, Object value) {
        NOTES.put(key, String.valueOf(value));
    }

    /** A GL error now, if any, noted under {@code where}. */
    public static void glError(String where) {
        int e = GL11.glGetError();
        if (e != GL11.GL_NO_ERROR) {
            count("glError@" + where + "=" + e);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - last < 5000) {
            return;
        }
        last = now;
        StringBuilder sb = new StringBuilder("Shadered+ check: shaders");
        shader(sb, "aquarium", AquariumRenderer.shader);
        shader(sb, "koi_pond", KoiPondRenderer.shader);
        shader(sb, "lava_lamp", LavaLampRenderer.shader);
        shader(sb, "wall_fire", WallFireRenderer.shader);
        shader(sb, "dance_floor", DanceFloorRenderer.shader);
        shader(sb, "neon_sign", NeonSignRenderer.shader);
        shader(sb, "jelly_tank", JellyTankRenderer.shader);
        shader(sb, "hologram", HologramRenderer.shader);
        shader(sb, "visualizer", VisualizerRenderer.shader);
        shader(sb, "infinity_cube", MirrorShaders.infinityCube);
        sb.append(" | counts (5 s) ").append(COUNTS).append(" | ").append(NOTES);
        SkyBlocksMod.LOGGER.info(sb.toString());
        COUNTS.clear();
    }

    private static void shader(StringBuilder sb, String name, Object shader) {
        sb.append(' ').append(name).append(shader == null ? "=MISSING" : "=ok");
    }
}
