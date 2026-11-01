package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Loads our core shaders one by one, so one that fails is reported (log line "Shadered+ shader ...") instead of
 * silently taking the others with it; after each reload, a summary of which loaded.
 */
@EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShaderLoad {
    private static final Map<String, String> STATUS = new TreeMap<>();
    private static boolean reported = true;

    private ShaderLoad() {}

    public static void register(RegisterShadersEvent event, ResourceLocation name, VertexFormat format, Consumer<ShaderInstance> onLoad) {
        reported = false;
        STATUS.put(name.getPath(), "asked");
        try {
            ShaderInstance shader = new ShaderInstance(event.getResourceProvider(), name, format);
            event.registerShader(shader, s -> {
                STATUS.put(name.getPath(), "ok");
                onLoad.accept(s);
            });
            STATUS.put(name.getPath(), "built");
        } catch (Throwable e) {
            StringBuilder why = new StringBuilder();
            for (Throwable t = e; t != null && why.length() < 600; t = t.getCause()) {
                why.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" <- ");
            }
            STATUS.put(name.getPath(), "FAILED " + why);
            SkyBlocksMod.LOGGER.error("Shadered+ shader {} failed to load: {}", name, why, e);
        }
    }

    @SubscribeEvent
    public static void onShaderLoadTick(ClientTickEvent.Post event) {
        if (!reported) {
            reported = true;
            SkyBlocksMod.LOGGER.info("Shadered+ shaders after reload: {}", STATUS);
        }
    }
}
