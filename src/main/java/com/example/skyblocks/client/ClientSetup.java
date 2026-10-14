package com.example.skyblocks.client;

import com.example.skyblocks.ModBlockEntities;
import com.example.skyblocks.OiiaCatBlockEntity;
import com.example.skyblocks.SkyBlocksMod;
import com.example.skyblocks.client.island.IslandScannerRenderer;
import com.example.skyblocks.client.island.SkyIslandRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;

@Mod.EventBusSubscriber(modid = SkyBlocksMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** Add our skies' skybox passes to Shadered's renderer (Shadered adds its own in its client setup). */
    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // The Island Painting's window needs a stencil buffer (client/island/IslandWindow).
        event.enqueueWork(() -> net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().enableStencil());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OIIA_CAT.get(), OiiaCatRenderer::new);
        BlockEntityRendererProvider<OiiaCatBlockEntity> big = OiiaCatRenderer::new;
        event.registerBlockEntityRenderer(ModBlockEntities.BIG_OIIA_CAT.get(), big);
        event.registerBlockEntityRenderer(ModBlockEntities.SKY_ISLAND.get(), SkyIslandRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ISLAND_SCANNER.get(), IslandScannerRenderer::new);
    }

    private ClientSetup() {}
}
