package com.example.skyblocks.client.iris;

import com.example.skyblocks.SkyBlocksMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The shader that draws Island Paintings and island cats over a shader pack's image; see {@link IrisIslandTarget}. */
@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class IrisShaders {
    public static ShaderInstance overlay;
    /** Writes the depth of a painting's picture into the target's painting depth layer. */
    public static ShaderInstance pictureDepth;
    /** Copies Minecraft's main depth into Shadered+'s own depth texture. */
    public static ShaderInstance depthCopy;

    private IrisShaders() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "iris_island_overlay"), DefaultVertexFormat.POSITION_TEX, shader -> overlay = shader);
        com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "iris_picture_depth"), DefaultVertexFormat.POSITION, shader -> pictureDepth = shader);
        com.example.skyblocks.client.ShaderLoad.register(event, new ResourceLocation(SkyBlocksMod.MOD_ID, "iris_depth_copy"), DefaultVertexFormat.POSITION_TEX, shader -> depthCopy = shader);
    }
}
