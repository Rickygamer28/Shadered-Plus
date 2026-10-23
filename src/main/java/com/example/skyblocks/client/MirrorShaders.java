package com.example.skyblocks.client;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The mirror face shader (shows its wall's reflection, looked up where the picture was taken). */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MirrorShaders {
    public static ShaderInstance surface;
    /** The infinity mirror's tunnel (InfinityMirrorRenderer). */
    public static ShaderInstance infinity;
    /** The infinity cube's endless grid (InfinityCubeRenderer). */
    public static ShaderInstance infinityCube;

    private MirrorShaders() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(SkyBlocksMod.MOD_ID, "mirror_surface"), DefaultVertexFormat.POSITION),
                shader -> surface = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(SkyBlocksMod.MOD_ID, "infinity_mirror"), DefaultVertexFormat.POSITION),
                shader -> infinity = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        new ResourceLocation(SkyBlocksMod.MOD_ID, "infinity_cube"), DefaultVertexFormat.POSITION),
                shader -> infinityCube = shader);
    }
}
